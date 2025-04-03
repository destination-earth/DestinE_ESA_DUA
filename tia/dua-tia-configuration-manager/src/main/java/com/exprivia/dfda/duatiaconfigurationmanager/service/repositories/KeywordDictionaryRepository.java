package com.exprivia.dfda.duatiaconfigurationmanager.service.repositories;

import org.springframework.data.keyvalue.repository.KeyValueRepository;
import org.springframework.stereotype.Repository;

import com.exprivia.dfda.tia.model.KeywordDictionary;

@Repository
public interface KeywordDictionaryRepository extends KeyValueRepository<KeywordDictionary, String> {

}
