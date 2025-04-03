package com.exprivia.dfda.duatiaconfigurationmanager.service.models;

import java.util.List;

import io.micrometer.common.lang.NonNull;
import lombok.Data;

@Data
public class RepositorySearchBindingReference {
    @NonNull
    private Integer initiativeIdx;

    @NonNull
    private Integer documentTypeIdx;

    @NonNull
    private Integer keywordDictionaryIdx;

    @NonNull
    private List<Integer> documentRepositoryIdxs;

}
