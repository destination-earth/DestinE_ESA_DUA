package com.exprivia.dfda.duatiaconfigurationmanager.service.services;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.DocumentRepositoryRepository;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.QueryMaxPublicationDateRepository;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.RepositorySearchBindingRepository;
import com.exprivia.dfda.tia.model.QueryMaxPublicationDate;

@Service
public class QueryMaxPublicationDateService {
    @Autowired
    private QueryMaxPublicationDateRepository queryMaxPublicationDateRepository;

    @Autowired
    private RepositorySearchBindingRepository searchBindingRepository;

    @Autowired
    private DocumentRepositoryRepository repositoryRepository;

    public QueryMaxPublicationDate getMaxQueryPublicationDate(String id) {
        return queryMaxPublicationDateRepository.findById(id).orElse(null);
    }

    public String saveMaxQueryPublicationDate(QueryMaxPublicationDate queryMaxPublicationDate) {
        if (!searchBindingRepository.existsById(queryMaxPublicationDate.getSearchBindingId())) {
            throw new NoSuchElementException(
                "repository search binding " + queryMaxPublicationDate.getSearchBindingId() + " does not exist");
        }
        if (!repositoryRepository.existsById(queryMaxPublicationDate.getRepositoryId())) {
            throw new NoSuchElementException(
                "repository " + queryMaxPublicationDate.getRepositoryId() + " does not exist");
        }

        QueryMaxPublicationDate qmpd = queryMaxPublicationDateRepository.save(queryMaxPublicationDate);
        return qmpd.getId();
    }

    public void deleteMaxQueryPublicationDate(String id) {
        QueryMaxPublicationDate qmpd = queryMaxPublicationDateRepository.findById(id).orElseThrow();

        queryMaxPublicationDateRepository.delete(qmpd);
    }

    public List<QueryMaxPublicationDate> getQueryMaxPublicationDateList() {
        return queryMaxPublicationDateRepository.findAll();
    }
}
