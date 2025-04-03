package com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions;

public class CannotContactDocumentRepositoryException extends Exception {
    public CannotContactDocumentRepositoryException(String message) {
        super(message);
    }

    public CannotContactDocumentRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
