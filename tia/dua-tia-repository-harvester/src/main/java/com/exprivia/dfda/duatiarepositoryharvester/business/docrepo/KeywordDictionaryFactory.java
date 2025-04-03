package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo;

import java.io.File;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.exprivia.dfda.tia.model.KeywordDictionary;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class KeywordDictionaryFactory {
    @Value("${dua.tia.repositoryharvester.keywords.config-file}")
    private String keywordsConfigFile;

    // This is loaded only in query-mode to setup keyword dictionary from file

    @Bean
    public KeywordDictionary getKeywordDictionary() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        File configFile = new File(keywordsConfigFile);
        if (!configFile.exists()) {
            throw new IOException("config file does not exist: " + configFile.getAbsolutePath());
        }

        return mapper.readValue(configFile, KeywordDictionary.class);
    }

}