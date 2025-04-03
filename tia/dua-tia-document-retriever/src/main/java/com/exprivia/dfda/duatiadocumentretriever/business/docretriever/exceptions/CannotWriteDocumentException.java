package com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions;

public class CannotWriteDocumentException extends Exception {

    public CannotWriteDocumentException(String message) {
        super(message);
    }

    public CannotWriteDocumentException(String message, Throwable cause) {
        super(message, cause);
    }

}
