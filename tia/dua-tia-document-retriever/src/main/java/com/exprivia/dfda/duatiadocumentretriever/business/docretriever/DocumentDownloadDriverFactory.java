package com.exprivia.dfda.duatiadocumentretriever.business.docretriever;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.driver.DocumentDownloadDriverInterface;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.driver.implementation.DocumentDownloadDriverScopus;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.driver.implementation.DocumentDownloadDriverSimple;

@Component
public class DocumentDownloadDriverFactory {
    @Autowired
    private DocumentDownloadDriverSimple simpleDownloader;

    @Autowired
    private DocumentDownloadDriverScopus scopusDownloader;

    public DocumentDownloadDriverInterface getDriver(String driverName) {
        if (driverName.equals(simpleDownloader.getDriverName())) return simpleDownloader;
        if (driverName.equals(scopusDownloader.getDriverName())) return scopusDownloader;

        throw new IllegalArgumentException("unrecognized document download driver: " + driverName);
    }
}
