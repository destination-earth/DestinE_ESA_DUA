package com.exprivia.dfda.tia.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.sql.Timestamp;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class DocumentRepository {
    // if id is null, during creation a uuid will be assigned
    private String id;

    @NonNull
    private String name;

    @NonNull
    private String url;

    @NonNull
    private String driver;

    private String credentials;

    /**
     * visiting frequency, expressed in seconds
     */
    @NonNull
    private Integer frequency;

    @NonNull
    private Boolean enabled;

    private Integer pageLimit;

    private Integer pauseBetweenPages;

    /**
     * last actual succesfull visit of the repository
     */
    private Timestamp lastVisit;
}
