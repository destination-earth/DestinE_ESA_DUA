package com.exprivia.dfda.tia.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.stereotype.Component;

import com.exprivia.dfda.duatiadocumentretriever.DuaTiaDocumentRetrieverConfiguration;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MetadataQueueService {
    @Autowired
    private DuaTiaDocumentRetrieverConfiguration config;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void pushMetadataEvent(String directory) {
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        setOps.add(config.getMetadataQueueName(), directory);
        log.info("pushed metadata event {} to {} queue", directory, config.getMetadataQueueName());
    }

    public String popMetadataEvent() {
        SetOperations<String, String> setOps = redisTemplate.opsForSet();
        String poppedData = setOps.pop(config.getMetadataQueueName());

        if (poppedData == null) {
            log.info("no metadata events available from {} queue", config.getMetadataQueueName());
            return null;
        }

        log.info("popped doc event {} from {} queue", poppedData, config.getMetadataQueueName());
        return poppedData;
    }
}
