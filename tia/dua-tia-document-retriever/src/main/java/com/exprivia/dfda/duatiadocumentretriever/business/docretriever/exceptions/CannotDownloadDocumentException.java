package com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions;

public class CannotDownloadDocumentException extends Exception {
    public CannotDownloadDocumentException(String message) {
        super(message);
    }

    public CannotDownloadDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
