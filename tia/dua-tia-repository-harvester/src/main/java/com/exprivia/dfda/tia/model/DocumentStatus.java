package com.exprivia.dfda.tia.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import lombok.Data;
import lombok.NonNull;

@Data
@RedisHash
public class DocumentStatus {

    @Id
    private String id;

    @NonNull
    private Instant downloadTimestamp;

    private boolean completed = false;

    private int retryCount = 0;

    static public String getId(String group, String repo, String mission, String docId) {
        return (group + "-" + repo + "-" + mission + "-" + docId).replace('/', '-');
    }

    public DocumentStatus() {

    }

    public DocumentStatus(String id, Instant downloadTimestamp) {
        this.id = id;
        this.downloadTimestamp = downloadTimestamp;
    }

    public DocumentStatus(String group, String repo, String mission, String docId, Instant downloadTimestamp) {
        this(
            getId(group, repo, mission, docId), 
            downloadTimestamp);
    }

    public DocumentStatus(String group, String repo, String mission, String docId, Instant downloadTimestamp, boolean completed, int retryCount) {
        this(
            getId(group, repo, mission, docId), 
            downloadTimestamp);
        this.completed = completed;
        this.retryCount = retryCount;
    }
}
