package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.result;

import java.util.ArrayList;
import java.util.List;

import com.exprivia.dfda.tia.model.DocumentCommonAttributes;

import lombok.Data;

@Data
public class DocumentRepositoryQueryResult {
    private List<DocumentCommonAttributes> recordList = new ArrayList<>();
}
