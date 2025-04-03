package com.exprivia.dfda.tia.model;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
public class RepositorySearchBinding {
    // if id is null, during creation a uuid will be assigned
    private String id;

    @NonNull
    private String initiativeId;

    @NonNull
    private String documentTypeId;

    @NonNull
    private String keywordDictionaryId;

    @NonNull
    private List<String> documentRepositoryIds;

    public RepositorySearchBinding(
        String initiativeId, 
        String documentTypeId, 
        String keywordDictionaryId, 
        List<String> documentRepositoryIds) {
        
        this.initiativeId = initiativeId;
        this.documentTypeId = documentTypeId;
        this.keywordDictionaryId = keywordDictionaryId;
        this.documentRepositoryIds = documentRepositoryIds;
    }
}
