package com.exprivia.dfda.duatiadocumentretriever.business.docretriever.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.springframework.stereotype.Component;

import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.download.DocumentDownloadResult;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DownloadLoggerUtil {
    private static final String LOG_NAME = "download-error.log";

    public void deleteLog(File path) {
        File previousLogFile = new File(path, LOG_NAME);
        if (previousLogFile.exists()) {
            if (previousLogFile.delete()) {
                log.debug("previous log file succesfully deleted");
            } else {
                log.warn("cannot remove previous log file from path {}", path.getAbsolutePath());
            }
        }
    }

    public void writeLog(File path, DocumentDownloadResult downloadResult) {
        try {
            writeLogFile(path, downloadResult.getErrors());
        } catch (IOException e) {
            log.error("cannot write log file", e);
        }
    }

    private void writeLogFile(File localDirectory, List<Object> lines) throws IOException {
        FileOutputStream fos = new FileOutputStream(new File(localDirectory, LOG_NAME), false);
        PrintStream printStream = new PrintStream(fos);

        for (Object line : lines) {
            if (line instanceof String) {
                printStream.append((String)line);
            } else if (line instanceof Throwable) {
                ((Throwable)line).printStackTrace(printStream);
            } else {
                log.error("trying to write to download log an unrecognized object {}", line);
            }
            printStream.append("\n");
        }

        printStream.close();
    }
}
