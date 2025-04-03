package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.driver.implementation;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.DocumentRepositoryAccess;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.driver.DocumentRepositoryDriverInterface;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.CannotContactRepositoryException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.CannotDecodeRepositoryResultsException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.UnexpectedJsonStructureException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.query.DocumentRepositoryQuery;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.result.DocumentRepositoryQueryResult;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.util.JsonUtil;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.util.RestUtil;
import com.exprivia.dfda.tia.model.DocumentCommonAttributes;
import com.exprivia.dfda.tia.model.DownloadDriverEnum;
import com.exprivia.dfda.tia.model.DocumentCommonAttributes.PublicationType;
import com.exprivia.dfda.tia.model.KeywordDictionary.KeywordDictionaryDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DocumentRepositoryDriverScopus implements DocumentRepositoryDriverInterface {
    @Autowired
    private JsonUtil jsonUtil;

    @Autowired
    private RestUtil restUtil;

    @Autowired
    private ObjectMapper mapper;

    private static final String DRIVER_NAME = "scopus";

    private static final String HEADER_API_KEY = "X-ELS-APIKey";

    private static final String QUERY_URL_PATH = "/search/scopus";
    private static final String DOWNLOAD_URL_PATH = "/article/doi/";

    private static final String DOI_RESOLVER_URL = "https://www.doi.org/";

    @Override
    public String getDriverName() {
        return DRIVER_NAME;
    }

    @Override
    public DocumentRepositoryQueryResult query(String groupName, DocumentRepositoryAccess repoAccess, DocumentRepositoryQuery predicate)
            throws CannotContactRepositoryException, CannotDecodeRepositoryResultsException {
        
        ScopusQueryTranslator scopusQuery = new ScopusQueryTranslator(predicate);
        String strQuery = scopusQuery.toString();

        if (repoAccess.getPageLimit() != null) {
            log.warn("configured page limit not implemented for Scopus");
        }

        log.info("query = {}", strQuery);

        // do the actual query on remote repository
        String repoUrl = repoAccess.getUrl().toString().replaceAll("/+$", "") + QUERY_URL_PATH + "?" + strQuery;

        HttpHeaders headers = new HttpHeaders();
        if (repoAccess.getCredentials() != null) {
            headers.add(HEADER_API_KEY, repoAccess.getCredentials());
            log.info("using credentials: {}", repoAccess.getCredentials());
        }

        HttpEntity<String> entity = new HttpEntity<String>(null, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = null;
        try {
            response = restTemplate.exchange(repoUrl, HttpMethod.GET, entity, String.class);
        } catch (RestClientException e) {
            throw new CannotContactRepositoryException("cannot contact repository " + repoAccess.getUrl(), e);
        }
        if (response.getStatusCode() != HttpStatus.OK) {
            throw new CannotContactRepositoryException("Error " + response.getStatusCode() + " contacting repo at url " + repoAccess.getUrl().toString());
        }

        restUtil.logRateLimitHeaders(response.getHeaders(), "X-RateLimit");

        try {
            JsonNode root = mapper.readTree(response.getBody());
            log.debug("response json: {}", root);

            /*
             * parse the query results, extracting the document title,
             * authors, online url and all the other important metadata
             */
            ScopusResultsTranslator resultTranslator = new ScopusResultsTranslator(groupName, root, repoAccess);
            return resultTranslator.translate();
        } catch (JsonProcessingException e) {
            throw new CannotContactRepositoryException("cannot parse json response", e);
        }
    }

    @Override
    public String getQueryAsString(DocumentRepositoryQuery query, int pageLimit) {
        ScopusQueryTranslator repoQuery = new ScopusQueryTranslator(query);
        return repoQuery.toString();
    }

    private class ScopusQueryTranslator {
        private DocumentRepositoryQuery predicate;

        public ScopusQueryTranslator(DocumentRepositoryQuery predicate) {
            this.predicate = predicate;
        }

        public String toString() {
            StringBuilder s = new StringBuilder();
            s.append("start=");
            s.append(predicate.getPaginationRecordOffset());
            s.append("&");

            s.append("query=(");

            s.append(navigateConditions(predicate.getKeywords()));

            s.append(") AND OPENACCESS(1) AND DOCTYPE(\"ar\")");
            s.append(" AND NOT LANGUAGE(\"Russian\") AND NOT LANGUAGE(\"Chinese\")");

            return s.toString();
        }

        private String navigateConditions(KeywordDictionaryDefinition dict) {
            StringBuilder s = new StringBuilder();

            List<String> keywords = dict.getKeywords();

            if (dict.isSkipKeywords() == false ) {
                int j = 0;
                if (keywords.size() > 1) s.append("(");
                for (String keyword : dict.getKeywords()) {
                    s.append("TITLE-ABS-KEY(\"" + keyword + "\")");

                    if (++j < dict.getKeywords().size()) {
                        s.append(" OR ");
                    }
                }
                if (keywords.size() > 1) s.append(")");
            }

            List<KeywordDictionaryDefinition> children = dict.getChildren();
            if (!children.isEmpty()) {
                if (dict.isSkipKeywords() == false) {
                    s.append(" AND ");
                }
                if (children.size() > 1) s.append("(");
                
                int i = 0;
                for (KeywordDictionaryDefinition subDict : children) {
                    s.append(navigateConditions(subDict));

                    if (++i < children.size()) {
                        s.append(" OR ");
                    }
                }
                if (children.size() > 1) s.append(")");
            }

            return s.toString();
        }
    }

    private class ScopusResultsTranslator {
        private String groupName;
		private JsonNode contents;
        private DocumentRepositoryAccess repositoryAccess;

        /*
        private static Map<String, String> doctypes = new HashMap<>() {{
            put("ar", "Article");
            put("ab", "Abstract Report");
            put("bk", "Book");
            put("ch", "Book Chapter");
            put("bz", "Business Article");
            put("cp", "Conference Paper");
            put("cr", "Conference Review");
            put("dp", "Data Paper");
            put("ed", "Editorial");
            put("er", "Erratum");
            put("le", "Letter");
            put("no", "Note");
            put("pr", "Press Release");
            put("rp", "Report");
            put("tb", "Retracted");
            put("re", "Review");
            put("sh", "Short Survey");
        }};
        */

		public ScopusResultsTranslator(String groupName, JsonNode contents, DocumentRepositoryAccess repositoryAccess) {
            this.groupName = groupName;
			this.contents = contents;
            this.repositoryAccess = repositoryAccess;
		}

		private DocumentRepositoryQueryResult translate() throws CannotDecodeRepositoryResultsException {
            DocumentRepositoryQueryResult results = new DocumentRepositoryQueryResult();

            // translate result records
            JsonNode resultsNode = contents.get("search-results");
            if (resultsNode == null) {
                throw new CannotDecodeRepositoryResultsException("missing required node \"search-results\" in response");
            }
                
            if (!resultsNode.isObject()) {
                throw new CannotDecodeRepositoryResultsException("\"search-results\" node is not an object");
            }

            JsonNode entryNode = resultsNode.get("entry");
            if (entryNode != null) {
                if (!entryNode.isArray()) {
                    throw new CannotDecodeRepositoryResultsException("\"entry\" node is not an object");
                }

                for (JsonNode docRecord : entryNode) {
                    if (docRecord.get("error") == null) {

                        results.getRecordList().add(
                            translateRecord(docRecord)
                        );
                    }
                }
            }
   
            return results;
		}

        private DocumentCommonAttributes translateRecord(JsonNode record) throws CannotDecodeRepositoryResultsException {
            try {
                String id = jsonUtil.getStringNodeValue(record, "dc:identifier", true);
                String doi = jsonUtil.getStringNodeValue(record, "prism:doi", false);

                String downloadUrl = repositoryAccess.getUrl().toString().replaceAll("/+$", "") + 
                    DOWNLOAD_URL_PATH + doi;
                
                List<String> downloadUrlList = new ArrayList<>();
                downloadUrlList.add(downloadUrl);
                if (doi != null) downloadUrlList.add(DOI_RESOLVER_URL + doi);

                List<String> journalTitles = jsonUtil.getStringArrayNodeValue(record, "prism:publicationName", false, true);

                List<String> issns = jsonUtil.getStringArrayNodeValue(record, "prism:issn", false, true);
                List<String> essns = jsonUtil.getStringArrayNodeValue(record, "prism:eIssn", false, true);
                if (essns != null && essns.size() > 0) {
                    issns.addAll(essns);
                }
                issns.removeIf(String::isEmpty);
                
                DocumentCommonAttributes docRecord = DocumentCommonAttributes.builder()
                    .id(id)
                    .group(groupName)
                    .repository(
                        DocumentCommonAttributes.Repository.builder()
                        .driver(getDriverName())
                        .url(repositoryAccess.getUrl().toString())
                        .credentials(repositoryAccess.getCredentials())
                        .build())
                    .document(
                        DocumentCommonAttributes.Document.builder()
                        .title(jsonUtil.getStringNodeValue(record, "dc:title", true))
                        .doi(doi == null ? null : Arrays.asList(doi))
                        .authors(Arrays.asList(jsonUtil.getStringNodeValue(record, "dc.creator", false)))
                        .editors(null)
                        .citations(null)
                        .publishedOn(jsonUtil.getDateNodeValue(record, "prism:coverDate", false))
                        .createdOn(jsonUtil.getDateNodeValue(record, "prism:coverDate", false))
                        .build())
                    .downloadInfo(
                        DocumentCommonAttributes.DownloadInfo.builder()
                        .documentUrl(downloadUrlList)
                        .downloadDriver(DownloadDriverEnum.SCOPUS_DOWNLOADER.name())
                        .credentials(repositoryAccess.getCredentials())
                        .build())
                    .publicationDetails(
                        DocumentCommonAttributes.PublicationDetails.builder()
                        .isOpenAccess(jsonUtil.getBoolNodeValue(record, "openaccessFlag", false))
                        .publicationType(PublicationType.fromString(jsonUtil.getStringNodeValue(record, "subtypeDescription", false)))
                        .publicationTypeString(jsonUtil.getStringNodeValue(record, "subtypeDescription", false))
                        .citationCount(Integer.valueOf(jsonUtil.getStringNodeValue(record, "citedby-count", false)))
                        .journalTitles(journalTitles)
                        .journalIssns(issns)
                        .journalImpactFactor(null)
                        .build())
                    .rawSearchResultRecord(record.toString())
                    .build();
                return docRecord;
            } catch (UnexpectedJsonStructureException e) {
                throw new CannotDecodeRepositoryResultsException("error decoding JRC Json document record", e);
            }
        }
    }
}
