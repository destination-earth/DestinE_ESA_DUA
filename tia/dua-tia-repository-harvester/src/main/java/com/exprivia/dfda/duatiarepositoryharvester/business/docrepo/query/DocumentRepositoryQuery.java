package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.query;

import java.util.Date;

import com.exprivia.dfda.tia.model.KeywordDictionary.KeywordDictionaryDefinition;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class DocumentRepositoryQuery {
    @NonNull
    private KeywordDictionaryDefinition keywords;

    private Date startDate;

    private Integer paginationPageNumber;
    private Integer paginationRecordOffset;
}
