package com.exprivia.dfda.duatiascheduler.service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.exprivia.dfda.tia.model.Credential;
import com.exprivia.dfda.tia.model.DocumentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URL;
import java.util.List;

@Configuration
public class DocumentRepositoriesConfig {
    @Value("${dua.tia.configuration-manager.repositories-url}")
    private URL repositoriesConfigURL;

    @Value("${dua.tia.configuration-manager.credentials-url}")
    private URL credentialsConfigURL;

    @Bean
    public DocumentRepositoriesConfigHandler getRepositoriesConfig() {
        return new DocumentRepositoriesConfigHandler();
    }

    public class DocumentRepositoriesConfigHandler {
        public List<DocumentRepository> getRepositoriesConfiguration() throws IOException {
            ObjectMapper mapper = new ObjectMapper();
    
            // read credentials
            List<Credential> credentials = mapper.readValue(credentialsConfigURL, new TypeReference<List<Credential>>() {});
    
            // read repositories configuration
            List<DocumentRepository> repos = mapper.readValue(repositoriesConfigURL, new TypeReference<List<DocumentRepository>>() {});
    
            // put credentials in repositories configuration
            for (Credential credential : credentials) {
                for (DocumentRepository repository : repos) {
                    if (repository.getId().equals(credential.getId())) {
                        repository.setCredentials(credential.getValue());
                    }
                }
            }
    
            return repos;
        }
    }

}
