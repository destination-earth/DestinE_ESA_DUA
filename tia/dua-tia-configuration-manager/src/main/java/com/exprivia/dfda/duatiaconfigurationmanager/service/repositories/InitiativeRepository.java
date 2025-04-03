package com.exprivia.dfda.duatiaconfigurationmanager.service.repositories;

import org.springframework.data.keyvalue.repository.KeyValueRepository;
import org.springframework.stereotype.Repository;

import com.exprivia.dfda.tia.model.Initiative;

@Repository
public interface InitiativeRepository extends KeyValueRepository<Initiative, String> {

}
