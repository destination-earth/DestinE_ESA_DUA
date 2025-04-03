package com.exprivia.dfda.duatiaconfigurationmanager.service.repositories;

import org.springframework.data.keyvalue.repository.KeyValueRepository;
import org.springframework.stereotype.Repository;

import com.exprivia.dfda.tia.model.DocumentType;

@Repository
public interface DocumentTypeRepository extends KeyValueRepository<DocumentType, String> {

}
