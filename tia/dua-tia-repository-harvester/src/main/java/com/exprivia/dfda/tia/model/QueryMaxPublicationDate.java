package com.exprivia.dfda.tia.model;

import java.util.Date;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
public class QueryMaxPublicationDate {

    private String id;

    @NonNull
    private String searchBindingId;

    @NonNull
    private String repositoryId;

    @NonNull
    private String mission;

    @NonNull
    private Date maxPublicationDate;

    public static String generateId(String searchBindingId, String repositoryId, String mission) {
        return searchBindingId + "-" + repositoryId + "-" + mission;
    }

    public QueryMaxPublicationDate(String searchBindingId, String repositoryId, String mission, Date maxPublicationDate) {
        this.id = generateId(searchBindingId, repositoryId, mission);
        this.searchBindingId = searchBindingId;
        this.repositoryId = repositoryId;
        this.mission = mission;
        this.maxPublicationDate = maxPublicationDate;
    }
}
