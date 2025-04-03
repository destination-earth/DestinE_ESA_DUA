package com.exprivia.dfda.tia.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.exprivia.dfda.tia.model.DocumentStatus;
import com.exprivia.dfda.tia.repository.DocumentStatusRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DocumentStatusService {
    @Autowired
    private DocumentStatusRepository repository;

    public void saveEntity(DocumentStatus entity) {
        repository.save(entity);
        if (log.isDebugEnabled()) {
            log.debug("REDIS: saved entity {}: {}", entity.getId(), entity);
        } else {
            log.info("REDIS: saved entity {}", entity.getId());
        }
    }

    public DocumentStatus getEntity(String group, String repo, String mission, String id) {
        String docId = DocumentStatus.getId(group, repo, mission, id);
        DocumentStatus ds = repository.findById(docId).orElse(null);
        
        if (log.isDebugEnabled()) {
            log.debug("REDIS: fetched entity {}: {}", docId, ds);
        } else {
            log.info("REDIS: fetched entity {}", docId);
        }

        return ds;
    }
}
