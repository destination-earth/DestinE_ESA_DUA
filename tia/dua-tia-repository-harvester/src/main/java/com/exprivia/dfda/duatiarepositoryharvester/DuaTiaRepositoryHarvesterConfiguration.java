package com.exprivia.dfda.duatiarepositoryharvester;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilderFactory;

import com.exprivia.dfda.tia.model.KeywordDictionary;
import com.exprivia.dfda.tia.model.QueryMaxPublicationDate;
import com.exprivia.dfda.tia.model.RepositorySearchBinding;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Data
@Slf4j
public class DuaTiaRepositoryHarvesterConfiguration {
    @Value("${dua.tia.repositoryharvester.full-text-max-size}")
    private Long fullTextMaxSize;

    @Value("${dua.tia.repositoryharvester.json-response-max-size}")
    private int jsonResponseMaxSize; 

    @Value("${dua.tia.repositoryharvester.querymode.output-file}")
    private String queryModeOutputFilename;

    @Value("${dua.tia.configurationmanager.repo-search-binding.url}")
    private String configurationManagerRepoSearchBindingUrl;

    @Value("${dua.tia.configurationmanager.keyword-dictionary.url}")
    private String configurationManagerKeywordDictionaryUrl;

    @Value("${dua.tia.configurationmanager.group-acronym.url}")
    private String configurationManagerGroupAcronymUrl;

    @Value("${dua.tia.configurationmanager.query-max-publication-date.url}")
    private String configurationManagerQueryMaxPublicationDateUrl;

    @Value("${dua.tia.configurationmanager.query-max-publication-date-update.url}")
    private String configurationManagerQueryMaxPublicationDateUpdateUrl;

    @Bean
    public ObjectMapper getMapper() {
        log.info("json response max size set to {}", jsonResponseMaxSize);

        ObjectMapper objectMapper = new ObjectMapper();
        StreamReadConstraints streamReadConstraints = StreamReadConstraints
            .builder()
            .maxStringLength(jsonResponseMaxSize)
            .build();
        objectMapper.getFactory().setStreamReadConstraints(streamReadConstraints);
        
        return objectMapper;
    }

    /*
     * this loads a list of pojo from the application properties
     */
    @Bean
    @ConfigurationProperties("dua.tia.repositoryharvester.querymode.repositories")
    public List<QueryModeRepository> getQueryModeRepositories() {
        return new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QueryModeRepository {
        @NonNull
        private String driver;
        @NonNull
        private String url;
        private String credentials;
        private Integer pageDelay;
        private Integer pageSize;
    }

    public List<RepositorySearchBinding> getRepositorySearchBindingsByRepositoryId(String repoId) {
        RestTemplate restTemplate = new RestTemplate();
        Map<String, String> params = new HashMap<>();
        params.put("repo-id", repoId);

        UriBuilderFactory factory = new DefaultUriBuilderFactory();
        try {
            URI uri = factory.uriString(configurationManagerRepoSearchBindingUrl).build(params).toURL().toURI();
            return Arrays.asList(restTemplate.getForObject(uri, RepositorySearchBinding[].class));
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException("Bad URL template in configuration " + configurationManagerRepoSearchBindingUrl, e);
        }
    }

    public KeywordDictionary getKeywordDictionary(String keywordDictionaryId) {
        RestTemplate restTemplate = new RestTemplate();
        Map<String, String> params = new HashMap<>();
        params.put("kwd-id", keywordDictionaryId);

        UriBuilderFactory factory = new DefaultUriBuilderFactory();
        try {
            URI uri = factory.uriString(configurationManagerKeywordDictionaryUrl).build(params).toURL().toURI();
            return restTemplate.getForObject(uri, KeywordDictionary.class);
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException("Bad URL template in configuration " + configurationManagerKeywordDictionaryUrl, e);
        }
    }

    public String getSearchGroupAcronymByBindingId(String bindingId) {
        RestTemplate restTemplate = new RestTemplate();
        Map<String, String> params = new HashMap<>();
        params.put("rsbinding-id", bindingId);

        UriBuilderFactory factory = new DefaultUriBuilderFactory();
        try {
            URI uri = factory.uriString(configurationManagerGroupAcronymUrl).build(params).toURL().toURI();
            return restTemplate.getForObject(uri, String.class);
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException("Bad URL template in configuration " + configurationManagerGroupAcronymUrl, e);
        }
    }

    public QueryMaxPublicationDate getQueryMaxPublicationDate(String repositorySearchBindingId, String repositoryId, String mission) {
        RestTemplate restTemplate = new RestTemplate();
        Map<String, String> params = new HashMap<>();
        params.put("rsbinding-id", repositorySearchBindingId);
        params.put("repo-id", repositoryId);
        params.put("mission", mission);

        UriBuilderFactory factory = new DefaultUriBuilderFactory();
        try {
            URI uri = factory.uriString(configurationManagerQueryMaxPublicationDateUrl).build(params).toURL().toURI();
            return restTemplate.getForObject(uri, QueryMaxPublicationDate.class);
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException("Bad URL template in configuration " + configurationManagerQueryMaxPublicationDateUrl, e);
        }
    }

    public void setQueryMaxPublicationDate(
        String repositorySearchBindingId, 
        String repositoryId, 
        String mission, 
        Date maxPublicationDate) {
        
        RestTemplate restTemplate = new RestTemplate();

        URI uri = URI.create(configurationManagerQueryMaxPublicationDateUpdateUrl);
        QueryMaxPublicationDate qmpd = new QueryMaxPublicationDate(
            repositorySearchBindingId, 
            repositoryId, 
            mission,
            maxPublicationDate);
        restTemplate.postForObject(uri, qmpd, QueryMaxPublicationDate.class);
    }

}
