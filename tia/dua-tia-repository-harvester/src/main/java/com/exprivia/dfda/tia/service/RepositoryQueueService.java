package com.exprivia.dfda.tia.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.stereotype.Component;

import com.exprivia.dfda.tia.model.DocumentRepository;
import com.exprivia.dfda.tia.service.exception.RepositoryQueueException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RepositoryQueueService {
    private static final String REPOSITORY_QUEUE_NAME = "repo";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void pushRepositoryEvent(DocumentRepository repo) throws RepositoryQueueException {
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        ObjectMapper mapper = new ObjectMapper();

        try {
            setOps.add(REPOSITORY_QUEUE_NAME, mapper.writeValueAsString(repo));
            log.info("pushed repo event {}", repo);
        } catch (JsonProcessingException e) {
            throw new RepositoryQueueException("cannot push repository event", e);
        }
    }

    public DocumentRepository popRepositoryEvent() throws RepositoryQueueException {
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        ObjectMapper mapper = new ObjectMapper();

        String poppedData = setOps.pop(REPOSITORY_QUEUE_NAME);

        if (poppedData == null) {
            log.info("no repo events available");
            return null;
        }

        try {
            DocumentRepository repo = mapper.readValue(poppedData, DocumentRepository.class);
            log.info("popped repo event {}", repo);
            return repo;
        } catch (JsonProcessingException e) {
            throw new RepositoryQueueException("cannot push repository event", e);
        }
    }
}
