package com.exprivia.dfda.duatiaconfigurationmanager.service.models;

import java.util.List;

import com.exprivia.dfda.tia.model.DocumentRepository;
import com.exprivia.dfda.tia.model.DocumentType;
import com.exprivia.dfda.tia.model.Initiative;
import com.exprivia.dfda.tia.model.KeywordDictionary;
import com.exprivia.dfda.tia.model.RepositorySearchBinding;

import lombok.Data;
import lombok.NonNull;

@Data
public class BulkConfiguration {
    @NonNull
    private List<Initiative> initiatives;
    
    @NonNull
    private List<DocumentType> documentTypes;

    @NonNull
    private List<DocumentRepository> documentRepositories;

    @NonNull
    private List<KeywordDictionary> keywordDictionaries;

    @NonNull
    private List<RepositorySearchBindingReference> repositorySearchBindingReferences;

    @NonNull
    private List<RepositorySearchBinding> repositorySearchBindings;
}
