package com.exprivia.dfda.duatiadocumentretriever.business;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.exprivia.dfda.duatiadocumentretriever.DuaTiaDocumentRetrieverConfiguration;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.DocumentDownloadDriverFactory;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.download.DocumentDownloadAction;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.download.DocumentDownloadResult;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.driver.DocumentDownloadDriverInterface;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions.CannotContactDocumentRepositoryException;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions.CannotDownloadDocumentException;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions.CannotWriteDocumentException;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.util.DataUtil;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.util.DownloadLoggerUtil;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.util.DownloadUtil;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.util.RedisDocumentProcessingStatus;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.util.DownloadUtil.ExternalServiceUriResolver;
import com.exprivia.dfda.tia.model.DocumentCommonAttributes;
import com.exprivia.dfda.tia.model.DocumentStatus;
import com.exprivia.dfda.tia.service.DocumentStatusService;
import com.exprivia.dfda.tia.service.MetadataQueueService;
import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DocumentRetriever {
    @Autowired
    private DocumentDownloadDriverFactory factory;

    @Autowired
    private DataUtil dataUtil;

    @Autowired
    private DocumentStatusService documentStatusService;

    @Autowired
    private MetadataQueueService metadataQueueService;

    @Autowired
    private DuaTiaDocumentRetrieverConfiguration config;

    @Autowired
    private DownloadLoggerUtil downloadLoggerUtil;

    @Autowired
    private DownloadUtil downloadUtil;

    @Autowired
    private RedisDocumentProcessingStatus documentProcessingStatus;
    
    public DocumentDownloadResult download(DocumentDownloadAction downloadAction, DocumentCommonAttributes docAttr) 
        throws CannotContactDocumentRepositoryException, CannotDownloadDocumentException, CannotWriteDocumentException {

        String repoDriverName = docAttr.getRepository().getDriver();
        String docId = docAttr.getId();
        String mission = docAttr.getDocument().getKeywordDictionarySecondLevel();
        String groupName = docAttr.getGroup();

        // Check if this document batch has been already downloaded
        DocumentStatus status = documentStatusService.getEntity(groupName, repoDriverName, mission, docId);
        if (status != null) {
            if (status.isCompleted()) {
                log.info("document {} {} already downloaded at {}", repoDriverName, docId, status.getDownloadTimestamp());
                return new DocumentDownloadResult(true, false, true);
            } else if (status.getRetryCount() <= 0) {
                log.info("document {} {} was not completed and has no retries left, skipped", repoDriverName, docId);
                return new DocumentDownloadResult(false, true, true);
            } else {
                log.info("document {} {} was not completed and has still {} retries left, retrying again", repoDriverName, docId, status.getRetryCount());
            }
        } else {
            // not yet downloaded, initialize document status
            status = new DocumentStatus(groupName, repoDriverName, mission, docId, null, false, config.getMaxRetryCount());
        }
        
        // create the download driver instance
        DocumentDownloadDriverInterface downloadDriver = factory.getDriver(downloadAction.getDownloadDriver());

        // create a temporary working directory to download multiple urls
        downloadAction.createLocalDirectory();

        // create a directory-bound id to propagate events
        // for metadata processing and document processing status
        String dirEventId = downloadAction.getDirEventId();
        File tempDir = downloadAction.getLocalDownloadPath();

        // put metadata json in the temporary folder
        createMetadata(tempDir, docAttr);

        if (docAttr.getDocument().getDoi() == null) {
            log.info("dois list is null, cannot create external service metadata for doc id {}", docId);
        } else {
            // write additional metadata json from unpaywall service
            if (config.isUnpaywallServiceMetadataEnabled()) {
                createExternalServiceMetadata(
                    downloadUtil.new ExternalServiceUriResolverUnpaywall(), 
                    tempDir, 
                    docAttr.getDocument().getDoi());
            }

            // write enrichment metadata json from cross-ref service
            if (config.isCrossRefServiceMetadataEnabled()) {
                createExternalServiceMetadata(
                    downloadUtil.new ExternalServiceUriResolverCrossRef(), 
                    tempDir, 
                    docAttr.getDocument().getDoi());
            }

            // write enrichment metadata json from open-citations service
            if (config.isOpenCitationsServiceMetadataEnabled()) {
                createExternalServiceMetadata(
                    downloadUtil.new ExternalServiceUriResolverOpenCitations(), 
                    tempDir, 
                    docAttr.getDocument().getDoi());
            }
        }

        // delete previous log, if exists
        downloadLoggerUtil.deleteLog(tempDir);
        documentProcessingStatus.initializeDownloadStatusValue(dirEventId);

        // actually download file
        DocumentDownloadResult downloadResult = downloadDriver.download(downloadAction);

        if (downloadResult.isSkipped()) {
            return downloadResult;
        }

        status.setDownloadTimestamp(Instant.now());
        if (downloadResult.isPartial() && 
            downloadResult.getFileName().size() < downloadResult.getMinimumRequired()) {
            status.setRetryCount(status.getRetryCount() - 1);
            status.setCompleted(false);
            
            // write a detailed download error log for the current document
            downloadLoggerUtil.writeLog(tempDir, downloadResult);

            documentProcessingStatus.setDownloadError(dirEventId, downloadResult.getErrors());
        } else {
            // set as completed even if not all files are downloaded but the minimum 
            // required number is reached
            status.setCompleted(true);
            documentProcessingStatus.setDownloadCompleted(dirEventId);

            // enqueue a trigger event for the metadata-retrieval-tool
            metadataQueueService.pushMetadataEvent(dirEventId);
        }

        documentStatusService.saveEntity(status);

        return downloadResult;
    }

    private void createMetadata(File tempDir, DocumentCommonAttributes docAttr) throws CannotWriteDocumentException {
        File metadataFile = new File(tempDir, "document-metadata.json");
        try {
            docAttr.getDocument().setFullText(
                dataUtil.decompress(
                    docAttr.getDocument().getFullText()));

            writeJson(metadataFile, docAttr);
        } catch (IOException e) {
            throw new CannotWriteDocumentException("cannot write metadata document " + metadataFile.getAbsolutePath(), e);
        }
    }

    private void createExternalServiceMetadata(ExternalServiceUriResolver uriResolver, File outputDir, List<String> dois) {
        String filePrefix = uriResolver.getServiceName();
        String fileExtension = ".json";
        File outputFile = new File(outputDir, filePrefix + fileExtension);
        for (int i = 0; i < dois.size(); i++) {
            String doi = dois.get(i);
            try {
                JsonNode n = downloadUtil.getJsonNodeFromURI(uriResolver.resolve(doi));
                writeJson(outputFile, n);
            } catch (Exception e) {
                log.error("cannot get " + filePrefix + " for doi " + doi + ": " + e.getMessage());
            }
            // name the next file (if any) with a trailing number
            outputFile = new File(outputDir, filePrefix + "-" + i + fileExtension);
        }
    }

    private void writeJson(File outputFile, Object jsonValue) throws StreamWriteException, DatabindException, IOException  {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        mapper.writeValue(outputFile, jsonValue);
    }
}
