package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions;

public class UnexpectedJsonStructureException extends Exception {

    public UnexpectedJsonStructureException(String message) {
        super(message);
    }

    public UnexpectedJsonStructureException(String message, Throwable cause) {
        super(message, cause);
    }

}
