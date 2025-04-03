package com.exprivia.dfda.duatiadocumentretriever.business.docretriever.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RedisDocumentProcessingStatus {
    private static final String KEY_PREFIX = "doc-processing-";
    private static final String STATUS_DOWNLOAD_MEMBER = "download";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void initializeDownloadStatusValue(String documentId) {
        setDownloadStatusValue(documentId, "started at " + Instant.now().toString());
    }

    public void setDownloadStatusValue(String documentId, String value) {
        HashOperations<String, Object, Object> hashOp = redisTemplate.opsForHash();

        String key = KEY_PREFIX + documentId;
        String member = STATUS_DOWNLOAD_MEMBER;
        
        try {
            hashOp.put(key, member, value);
        } catch (Exception e) {
            log.error("cannot write status to redis for document " + documentId + ": " + e.getMessage(), e);
        }
    }

    public void setDownloadError(String documentId, List<Object> value) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(os);

        ps.println("log time is " +  Instant.now().toString());

        for (Object line : value) {
            if (line instanceof String) {
                ps.append((String)line);
            } else if (line instanceof Throwable) {
                ((Throwable)line).printStackTrace(ps);
            } else {
                log.error("trying to write to download log an unrecognized object {}", line);
            }
        }
        
        try {
            String output = os.toString("UTF8");
            setDownloadStatusValue(documentId, output);
        } catch (UnsupportedEncodingException e) {
            log.error(e.getMessage(), e);
        }
    }

    public void setDownloadError(String documentId, String errorMessage) {
        setDownloadStatusValue(documentId, errorMessage);
    }

    public void setDownloadCompleted(String documentId) {
        setDownloadStatusValue(documentId, "completed at " + Instant.now().toString());
    }
}
