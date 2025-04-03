package com.exprivia.dfda.duatiaconfigurationmanager.service.models;

import lombok.Data;
import lombok.NonNull;

@Data
public class ResponseErrorMessage {
    @NonNull
    private String message;

    public ResponseErrorMessage(Throwable e) {
        message = e.getMessage();
    }
}
