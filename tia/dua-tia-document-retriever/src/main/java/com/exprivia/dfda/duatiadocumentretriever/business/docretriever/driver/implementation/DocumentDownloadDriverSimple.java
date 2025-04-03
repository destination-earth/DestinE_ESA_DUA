package com.exprivia.dfda.duatiadocumentretriever.business.docretriever.driver.implementation;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.exprivia.dfda.duatiadocumentretriever.DuaTiaDocumentRetrieverConfiguration;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.download.DocumentDownloadAction;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.download.DocumentDownloadResult;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.driver.DocumentDownloadDriverInterface;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions.CannotContactDocumentRepositoryException;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions.CannotDownloadDocumentException;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.util.DownloadUtil;
import com.exprivia.dfda.tia.model.DownloadDriverEnum;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DocumentDownloadDriverSimple implements DocumentDownloadDriverInterface {

    private static final String DRIVER_NAME = DownloadDriverEnum.SIMPLE_DOWNLOADER.name();

    @Autowired
    private DownloadUtil downloadUtil;

    @Autowired
    private DuaTiaDocumentRetrieverConfiguration config;

    @Override
    public String getDriverName() {
        return DRIVER_NAME;
    }

    @Override
    public DocumentDownloadResult download(DocumentDownloadAction downloadDocument) 
        throws CannotContactDocumentRepositoryException, CannotDownloadDocumentException {

        DocumentDownloadResult downloadResult = new DocumentDownloadResult();

        int downloadedDocuments = 0;
        int currentDocument = 0;

        for (String url : downloadDocument.getDocumentUrl()) {
            currentDocument++;
            if (url == null || url.trim().equals("")) {
                log.warn("empty download url for document {}", downloadDocument.getId());
                downloadResult.appendError("empty download url for document {}", downloadDocument.getId());
                continue;
            }

            if (handleSingleDownload(
                url, 
                downloadDocument, 
                downloadResult, 
                currentDocument)) {
                downloadedDocuments++;
            }

            if (downloadedDocuments == downloadDocument.getMinimumRequired() && 
                downloadDocument.getDocumentUrl().size() > downloadedDocuments) {
                log.info("minimum required downloads {} reached, skipping further download urls", downloadDocument.getMinimumRequired());
                break;
            }
        }

        if (downloadedDocuments == 0 && config.isUnpaywallServiceFallbackEnabled() && downloadDocument.getDoi() != null) {
            // as a fallback, request an additional download pdf url to unpaywall
            for (String doi : downloadDocument.getDoi()) {
                try {
                    String fallbackUrl = getDownloadUrlFromUnpaywall(doi);
                    log.info("retrieved fallback url {} for document {}", fallbackUrl, downloadDocument.getId());
                    if (handleSingleDownload(
                        fallbackUrl, 
                        downloadDocument, 
                        downloadResult, 
                        currentDocument)) {
                        downloadedDocuments++;
                    }
                } catch (IOException | IllegalArgumentException e) {
                    log.error("cannot get document or fallback url from unpaywall service for doi " + doi, e);
                    downloadResult.appendError("cannot get document or fallback url from unpaywall service for doi " + doi + ": " + e.getMessage());
                }
            }
        }
        
        downloadResult.setPartial(downloadedDocuments < downloadDocument.getDocumentUrl().size());
        downloadResult.setSucceded(downloadedDocuments > 0);
        downloadResult.setMinimumRequired(downloadDocument.getMinimumRequired());

        log.info("document download report for {}: dowloaded = {}, total = {}, required = {})",
            downloadDocument.getId(), 
            downloadedDocuments,
            downloadDocument.getDocumentUrl().size(),
            downloadDocument.getMinimumRequired());

        return downloadResult;
    }

    private boolean handleSingleDownload(
        String currentUrl, 
        DocumentDownloadAction downloadDocument,
        DocumentDownloadResult downloadResult,
        int currentDocument) {

        String url = currentUrl.replace(" ", "%20");
        int totalDocuments = downloadDocument.getDocumentUrl().size();
        int requiredDocuments = downloadDocument.getMinimumRequired();

        log.info("document download {} ({}/{}({})): url '{}' in progress", 
            downloadDocument.getId(), 
            currentDocument,
            totalDocuments,
            requiredDocuments,
            url);

        try {

            String localFile = downloadUtil.downloadFile(
                new URI(url), 
                downloadDocument.getLocalDownloadPath().getAbsolutePath(),
                downloadUtil.defaultHeaders());

            log.info("document download {} ({}/{}({})): url '{}' to '{}'", 
                downloadDocument.getId(), 
                currentDocument,
                totalDocuments,
                requiredDocuments,
                url,
                localFile);        

            downloadResult.getFileName().add(localFile);

            return true;

        } catch (MalformedURLException | URISyntaxException e) {
            log.error("document download {} ({}/{}({})): url '{}' is malformed {}",
                downloadDocument.getId(), 
                currentDocument,
                totalDocuments,
                requiredDocuments,
                url,
                e.getMessage());
            log.error("error details", e);

            downloadResult.appendError(
                "document download {} ({}/{}({})): url '{}' is malformed {}",
                downloadDocument.getId(), 
                currentDocument,
                totalDocuments,
                requiredDocuments,
                url,
                e.getMessage());
            downloadResult.appendError(e);
            return false;
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            // this can be a temporary issue, retry later
            log.error("document download {} ({}/{}({})): url '{}' cannot download file: {}",
                downloadDocument.getId(), 
                currentDocument,
                totalDocuments,
                requiredDocuments,
                url,
                e.getMessage());
            log.error("error details", e);

            downloadResult.appendError(
                "document download {} ({}/{}({})): url '{}' cannot download file: {}",
                downloadDocument.getId(), 
                currentDocument,
                totalDocuments,
                requiredDocuments,
                url,
                e.getMessage());
            downloadResult.appendError(e);
            return false;
        }
    }

    private String getDownloadUrlFromUnpaywall(String doi) throws IOException {
        JsonNode root = downloadUtil.getJsonNodeFromURI(
            downloadUtil.new ExternalServiceUriResolverUnpaywall().resolve(doi)
            );

        JsonNode bestOaLocation = root.get("best_oa_location");
        if (bestOaLocation == null) {
            throw new IOException("cannot find best_oa_location node for doi " + doi);
        }
        
        JsonNode urlForPdf = bestOaLocation.get("url_for_pdf");
        if (urlForPdf == null) {
            throw new IOException("cannot find url_for_pdf node for doi " + doi);
        }

        return urlForPdf.asText();
    }
}
