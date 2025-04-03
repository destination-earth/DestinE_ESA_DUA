package com.exprivia.dfda.duatiaconfigurationmanager.service.repositories;

import org.springframework.data.keyvalue.repository.KeyValueRepository;

import com.exprivia.dfda.tia.model.QueryMaxPublicationDate;

public interface QueryMaxPublicationDateRepository extends KeyValueRepository<QueryMaxPublicationDate, String> {

}
