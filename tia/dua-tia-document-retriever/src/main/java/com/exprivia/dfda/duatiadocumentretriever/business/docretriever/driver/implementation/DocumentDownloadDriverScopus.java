package com.exprivia.dfda.duatiadocumentretriever.business.docretriever.driver.implementation;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.download.DocumentDownloadAction;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.download.DocumentDownloadResult;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.driver.DocumentDownloadDriverInterface;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions.CannotContactDocumentRepositoryException;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions.CannotDownloadDocumentException;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.util.DownloadUtil;
import com.exprivia.dfda.tia.model.DownloadDriverEnum;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DocumentDownloadDriverScopus implements DocumentDownloadDriverInterface {

    @Autowired
    private DocumentDownloadDriverSimple downloadDriverSimple;

    @Autowired
    private DownloadUtil downloadUtil;

    private static final String DRIVER_NAME = DownloadDriverEnum.SCOPUS_DOWNLOADER.name();

    @Override
    public String getDriverName() {
        return DRIVER_NAME;
    }

    @Override
    public DocumentDownloadResult download(DocumentDownloadAction downloadDocument)
            throws CannotContactDocumentRepositoryException, CannotDownloadDocumentException {

        DocumentDownloadResult partialResult = new DocumentDownloadResult();

        String apiKey = downloadDocument.getRepositoryCredentials();

        /*
         * https://stackoverflow.com/questions/60050348/scopus-dois-not-working-in-article-retrieval-apis
         * 
         * The Article Retrieval API works for Elsevier content hosted on sciencedirect.com; all Elsevier articles 
         * have PII identifiers. The example DOI 10.1007/s10902-018-0038-4 does not work because it is published by 
         * Springer and, consequently, not available on ScienceDirect.
         * 
         * Kindly note that this is not a bug and everything is working as expected.
         * 
         */

        List<String> documentUrls = new ArrayList<>();
        int optionalDocuments = 0;
        for (String url : downloadDocument.getDocumentUrl()) {

            if (url != null && !url.trim().isEmpty()) {
                if (url.contains("elsevier.com")) {
                    // update document urls with apikey and file download format
                    URI uri = UriComponentsBuilder.fromUriString(url)
                        .queryParam("httpAccept", "application/pdf")
                        .queryParam("apiKey", apiKey)
                        .build().toUri();
                    url = uri.toString();
                }
                if (url.contains("doi.org")) {
                    // download via doi.org searching for a 
                    // citation_pdf_url tag like the following
                    // <meta name="citation_pdf_url" content="https://tc.copernicus.org/articles/17/2871/2023/tc-17-2871-2023.pdf"/>
                    // remember to use the HTTP header to skip the captcha check
                    // 'User-Agent: Mozilla/5.0 (X11; Linux x86_64; rv:125.0) Gecko/20100101 Firefox/125.0'
                    //
                    // check this as a working example:
                    // curl -L -H 'User-Agent: Mozilla/5.0 (X11; Linux x86_64; rv:125.0) Gecko/20100101 Firefox/125.0' "https://www.doi.org/10.1088/1748-9326/acd2ee" | grep -i pdf
                    URI realUri = null;
                    URI uri = null;
                    try {
                        uri = new URI(url);
                    } catch (URISyntaxException e) {
                        log.error("cannot interpret " + url + " as a valid url", e);
                        partialResult.appendError("cannot interpret {} as a valid url", url);
                        partialResult.appendError(e);
                        continue;
                    }
                    
                    realUri = resolveDownloadByDoi(uri, partialResult);
                    if (realUri == null) {
                        continue;
                    }
                    url = realUri.toString();
                    optionalDocuments++;
                }

                documentUrls.add(url);
            }
        }

        // update the urls in the download action
        downloadDocument.setDocumentUrl(documentUrls);

        // set the minimum required documents number excluding the
        // additional doi-translated documents
        downloadDocument.setMinimumRequired(documentUrls.size() - optionalDocuments);

        // process the downloads
        DocumentDownloadResult result = downloadDriverSimple.download(downloadDocument);

        // append errors from the Scopus specializations to the simple downloader errors list
        result.appendListOfErrors(
            partialResult.getErrors());

        return result;
    }

    private URI resolveDownloadByDoi(URI inputUri, DocumentDownloadResult downloadResult) {
        HttpResponse<String> response;
        String htmlContents = null;
        try {
            response = downloadUtil.downloadAsString(inputUri, downloadUtil.defaultHeaders());
            htmlContents = response.body();
        } catch (IOException|InterruptedException e) {
            log.error("cannot download html page from doi resolution for " + inputUri.toString(), e);
            downloadResult.appendError("cannot download html page from doi resolution for {}", inputUri.toString());
            downloadResult.appendError(e);
            return null;
        }

        // in some rare case the doi url is already a pdf, in this case
        // return the resolved uri as the downloadable pdf uri
        Optional<String> contentType = response.headers().firstValue(HttpHeaders.CONTENT_TYPE);
        if (contentType.isPresent() && contentType.get().equals("application/pdf")) {
            return response.uri();
        }

        log.debug("doi resolution html page ({}): {}", inputUri.toString(), htmlContents);

        // 1. search the <meta name="citation_pdf_url"> tag
        Pattern pattern = Pattern.compile("name=\"citation_pdf_url\"\\s+content=\"([=?&0-9a-zA-Z:.\\/_-]*)\"");

        Matcher m = pattern.matcher(htmlContents);
        if (m.find()) {
            try {
                return new URI(m.group(1));
            } catch (URISyntaxException e) {
                log.error("doi redirect url is not a valid uri ({}): {}", inputUri.toString(), m.group(1));
                downloadResult.appendError("doi redirect url is not a valid uri ({}): {}", inputUri.toString(), m.group(1));
                return null;
            }
        }

        // fallback methods of url retrieving
        // 2. check other possible links in the html page
        String[] patterns = { 
            "href=\"([=?&0-9a-zA-Z:.\\/_-]+pdf[=?&0-9a-zA-Z:.\\/_-]*)\"",
            "([=?&0-9a-zA-Z:.\\/_-]+pdf[=?&0-9a-zA-Z:.\\/_-]*)\""
        };
        for (String currentPattern : patterns) {
            pattern = Pattern.compile(currentPattern);

            m = pattern.matcher(htmlContents);
            if (m.find()) {
                try {
                    return DownloadUtil.makeAbsolute(response.uri(), new URI(m.group(1)));
                } catch (URISyntaxException e) {
                    log.error("doi fallback redirect url is not a valid uri ({}): {}", inputUri.toString(), m.group(1));
                    downloadResult.appendError("doi fallback redirect url is not a valid uri ({}): {}", inputUri.toString(), m.group(1));
                }
            }
        }

        log.warn("no url found in doi redirect html ({})", inputUri.toString());
        downloadResult.appendError("no url found in doi redirect html ({})", inputUri.toString());
        return null;
    }

}
