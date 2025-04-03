package com.exprivia.dfda.duatiadocumentretriever.business.docretriever.driver;

import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.download.DocumentDownloadAction;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.download.DocumentDownloadResult;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions.CannotContactDocumentRepositoryException;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions.CannotDownloadDocumentException;

public interface DocumentDownloadDriverInterface {

    public String getDriverName();

    public DocumentDownloadResult download(DocumentDownloadAction downloadDocument) 
        throws CannotContactDocumentRepositoryException, CannotDownloadDocumentException;

}
