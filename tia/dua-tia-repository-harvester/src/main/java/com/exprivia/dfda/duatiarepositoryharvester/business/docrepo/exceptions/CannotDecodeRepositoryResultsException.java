package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions;

public class CannotDecodeRepositoryResultsException extends Exception {
    public CannotDecodeRepositoryResultsException(String message) {
        super(message);
    }

    public CannotDecodeRepositoryResultsException(String message, Throwable error) {
        super(message, error);
    }
}
