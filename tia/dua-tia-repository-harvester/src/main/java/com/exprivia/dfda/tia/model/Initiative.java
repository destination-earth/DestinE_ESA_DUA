package com.exprivia.dfda.tia.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
public class Initiative {
    // if id is null, during creation a uuid will be assigned
    private String id;

    @NonNull
    private String name;

    @NonNull
    private String shortName;
}
