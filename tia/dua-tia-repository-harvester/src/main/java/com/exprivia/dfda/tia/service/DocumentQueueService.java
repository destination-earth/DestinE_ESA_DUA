package com.exprivia.dfda.tia.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.stereotype.Component;

import com.exprivia.dfda.tia.model.DocumentCommonAttributes;
import com.exprivia.dfda.tia.service.exception.DocumentQueueException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DocumentQueueService {
    private static final String DOCUMENT_QUEUE_NAME = "docs";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void pushDocumentEvent(DocumentCommonAttributes doc) throws DocumentQueueException {
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        ObjectMapper mapper = new ObjectMapper();

        try {
            setOps.add(DOCUMENT_QUEUE_NAME, mapper.writeValueAsString(doc));
            log.debug("pushed doc event {}", doc);
        } catch (JsonProcessingException e) {
            throw new DocumentQueueException("cannot push document event", e);
        }
    }

    public DocumentCommonAttributes popDocumentEvent() throws DocumentQueueException {
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        ObjectMapper mapper = new ObjectMapper();

        String poppedData = setOps.pop(DOCUMENT_QUEUE_NAME);

        if (poppedData == null) {
            log.info("no documents events available");
            return null;
        }

        try {
            DocumentCommonAttributes doc = mapper.readValue(poppedData, DocumentCommonAttributes.class);
            log.debug("popped doc event {}", doc);
            return doc;
        } catch (JsonProcessingException e) {
            throw new DocumentQueueException("cannot push document event", e);
        }
    }
}
