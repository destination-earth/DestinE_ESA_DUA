package com.exprivia.dfda.duatiaconfigurationmanager.service.repositories;

import org.springframework.data.keyvalue.repository.KeyValueRepository;
import org.springframework.stereotype.Repository;

import com.exprivia.dfda.tia.model.DocumentRepository;

@Repository
public interface DocumentRepositoryRepository extends KeyValueRepository<DocumentRepository, String> {

}
