package com.exprivia.dfda.duatiadocumentretriever.business;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.exprivia.dfda.duatiadocumentretriever.DuaTiaDocumentRetrieverConfiguration;
import com.exprivia.dfda.tia.model.DocumentStatus;
import com.exprivia.dfda.tia.repository.DocumentStatusRepository;

@Component
public class DuaTiaDocumentRetrieverDocStatusHelper {
    @Autowired
    private DocumentStatusRepository documentStatusRepository;

    @Autowired
    private DuaTiaDocumentRetrieverConfiguration config;

    public void query(boolean showCompleted, boolean showStillRetrying, boolean showFailed) {
        int count = 0;
        for (DocumentStatus docStatus : documentStatusRepository.findAll()) {
            if (docStatus.isCompleted() && showCompleted ||
                !docStatus.isCompleted() && docStatus.getRetryCount() > 0 && showStillRetrying ||
                !docStatus.isCompleted() && docStatus.getRetryCount() == 0 && showFailed) {

                printDocumentStatus(docStatus);
                
                count++;
            }
        }
        System.out.print("total=");
        System.out.println(count);
    }

    public void resetRetryCountForFailedFiles() {
        int count = 0;
        for (DocumentStatus docStatus : documentStatusRepository.findAll()) {
            if (!docStatus.isCompleted() && docStatus.getRetryCount() == 0) {

                docStatus.setRetryCount(config.getMaxRetryCount());
                documentStatusRepository.save(docStatus);
                
                printDocumentStatus(docStatus);

                count++;
            }
        }
        System.out.print("total=");
        System.out.println(count);
    }

    private void printDocumentStatus(DocumentStatus docStatus) {
        System.err.println(
            String.format("id=%s,completed=%b,retries-left=%d,timestamp=%s", 
                docStatus.getId(),
                docStatus.isCompleted(),
                docStatus.getRetryCount(),
                docStatus.getDownloadTimestamp())
            );    
    }
}
