package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions;

public class BadRepositoryConfigurationException extends Exception {
    
    public BadRepositoryConfigurationException(String errorMessage) {
        super(errorMessage);
    }

    public BadRepositoryConfigurationException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
