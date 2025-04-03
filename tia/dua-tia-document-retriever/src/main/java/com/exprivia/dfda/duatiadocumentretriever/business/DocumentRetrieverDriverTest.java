package com.exprivia.dfda.duatiadocumentretriever.business;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.DocumentDownloadDriverFactory;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.download.DocumentDownloadAction;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.download.DocumentDownloadResult;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.driver.DocumentDownloadDriverInterface;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions.CannotContactDocumentRepositoryException;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions.CannotDownloadDocumentException;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions.CannotWriteDocumentException;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.util.DownloadLoggerUtil;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DocumentRetrieverDriverTest {
    @Autowired
    private DocumentDownloadDriverFactory factory;

    @Autowired
    private DownloadLoggerUtil downloadLoggerUtil;

    public void testDownload(DocumentDownloadAction downloadAction) 
        throws CannotContactDocumentRepositoryException, CannotDownloadDocumentException, CannotWriteDocumentException{

        log.info("download document: {}", downloadAction.getId());
        log.info("from uri         : {}", downloadAction.getDocumentUrl().get(0));
        log.info("with credentials : {}", downloadAction.getRepositoryCredentials());
        log.info("to path          : {}", downloadAction.getLocalDownloadPath());

        DocumentDownloadDriverInterface driver = factory.getDriver(downloadAction.getDownloadDriver());
        log.info("using downloader : {}", downloadAction.getDownloadDriver());

        // create a temporary working directory to download multiple urls
        downloadAction.createLocalDirectory();
                
        DocumentDownloadResult result = driver.download(downloadAction);
        log.info("download results : {}", result);
        downloadLoggerUtil.writeLog(
            downloadAction.getLocalDownloadPath(), 
            result);
    }

}
