package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions;

import java.io.IOException;

public class CannotContactRepositoryException extends IOException {

    public CannotContactRepositoryException(String errorMessage) {
        super(errorMessage);
    }

    public CannotContactRepositoryException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
