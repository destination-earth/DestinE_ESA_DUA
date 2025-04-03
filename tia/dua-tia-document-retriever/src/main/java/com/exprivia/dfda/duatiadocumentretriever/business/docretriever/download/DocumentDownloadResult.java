package com.exprivia.dfda.duatiadocumentretriever.business.docretriever.download;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.helpers.MessageFormatter;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DocumentDownloadResult {

    private boolean succeded = false;

    private List<String> fileName = new ArrayList<>();

    private int minimumRequired;

    private boolean partial = true;

    private boolean skipped = false;

    List<Object> errors = new ArrayList<>();

    public DocumentDownloadResult(boolean succeded, boolean partial, boolean skipped) {
        this.succeded = succeded;
        this.partial = partial;
        this.skipped = skipped;
    }

    public void appendError(String format, Object... args) {
        errors.add(
            MessageFormatter.arrayFormat(format, args).getMessage()
            );
    }

    public void appendError(Throwable cause) {
        errors.add(cause);
    }

    public void appendListOfErrors(List<Object> list) {
        errors.addAll(list);
    }
}
