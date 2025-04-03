package com.exprivia.dfda.duatiaconfigurationmanager.service.exceptions;

public class ItemInUseException extends Exception {

    public ItemInUseException(String message) {
        super(message);
    }

    public ItemInUseException(String message, Throwable e) {
        super(message, e);
    }

}
