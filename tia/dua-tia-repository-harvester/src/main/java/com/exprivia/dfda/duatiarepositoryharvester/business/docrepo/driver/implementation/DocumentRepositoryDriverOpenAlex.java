package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.driver.implementation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.net.URI;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.DocumentRepositoryAccess;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.driver.DocumentRepositoryDriverInterface;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.CannotContactRepositoryException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.CannotDecodeRepositoryResultsException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.UnexpectedJsonStructureException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.query.DocumentRepositoryQuery;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.result.DocumentRepositoryQueryResult;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.util.JsonUtil;
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
public class DocumentRepositoryDriverOpenAlex implements DocumentRepositoryDriverInterface {
    @Autowired
    private JsonUtil jsonUtil;

    @Autowired
    private ObjectMapper mapper;

    private static final String DRIVER_NAME = "open_alex";

    @Override
    public String getDriverName() {
        return DRIVER_NAME;
    }

    @Override
    public DocumentRepositoryQueryResult query(String groupName, DocumentRepositoryAccess repoAccess, DocumentRepositoryQuery predicate)
            throws CannotContactRepositoryException, CannotDecodeRepositoryResultsException {

        OpenAlexQueryTranslator coreQuery = new OpenAlexQueryTranslator(predicate);
        String strQuery = coreQuery.toString();

        log.info("query = {}", strQuery);

        String repoUrl = repoAccess.getUrl() + "?" + strQuery;
        UriComponents uriComponents = UriComponentsBuilder.fromUriString(repoUrl).build().encode();

        log.debug("encoded url = {}", uriComponents.toUriString());

        // do the actual query on remote repository

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = null;
        try {
            response = restTemplate.getForEntity(uriComponents.toUri(), String.class);
        } catch (RestClientException e) {
            throw new CannotContactRepositoryException("cannot contact repository " + repoAccess.getUrl(), e);
        }
        if (response.getStatusCode() != HttpStatus.OK) {
            throw new CannotContactRepositoryException(
                    "Error " + response.getStatusCode() + " contacting repo at url " + repoAccess.getUrl().toString());
        }

        try {
            JsonNode root = mapper.readTree(response.getBody());
            log.debug("response json: {}", root);

            /*
             * parse the query results, extracting the document title,
             * authors, online url and all the other important metadata
             */
            OpenAlexResultsTranslator resultTranslator = new OpenAlexResultsTranslator(groupName, root, repoAccess);
            return resultTranslator.translate();
        } catch (JsonProcessingException e) {
            throw new CannotContactRepositoryException("cannot parse json response", e);
        }
    }

    @Override
    public String getQueryAsString(DocumentRepositoryQuery query, int pageLimit) {
        OpenAlexQueryTranslator repoQuery = new OpenAlexQueryTranslator(query);
        return repoQuery.toString();
    }

    private class OpenAlexQueryTranslator {
        private DocumentRepositoryQuery predicate;

        private static String FIXED_CONDITIONS = "is_oa:true,type:article";

        public OpenAlexQueryTranslator(DocumentRepositoryQuery predicate) {
            this.predicate = predicate;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append("filter=");
            sb.append(FIXED_CONDITIONS);
            sb.append(",");
            sb.append("title_and_abstract.search:");
            sb.append(navigateConditions(predicate.getKeywords()));

            sb.append("&");

            sb.append("page=");
            sb.append(predicate.getPaginationPageNumber() + 1);

            return sb.toString();
        }

        private String navigateConditions(KeywordDictionaryDefinition dict) {
            StringBuilder s = new StringBuilder();

            List<String> keywords = dict.getKeywords();

            if (dict.isSkipKeywords() == false) {
                int j = 0;
                if (keywords.size() > 1)
                    s.append("(");
                for (String keyword : dict.getKeywords()) {
                    s.append("\"" + keyword + "\"");

                    if (++j < dict.getKeywords().size()) {
                        s.append(" OR ");
                    }
                }
                if (keywords.size() > 1)
                    s.append(")");
            }

            List<KeywordDictionaryDefinition> children = dict.getChildren();
            if (!children.isEmpty()) {
                if (dict.isSkipKeywords() == false) {
                    s.append(" AND ");
                }
                if (children.size() > 1)
                    s.append("(");

                int i = 0;
                for (KeywordDictionaryDefinition subDict : children) {
                    s.append(navigateConditions(subDict));

                    if (++i < children.size()) {
                        s.append(" OR ");
                    }
                }
                if (children.size() > 1)
                    s.append(")");
            }

            return s.toString();
        }
    }

    private class OpenAlexResultsTranslator {
        private String groupName;
        private JsonNode contents;
        private DocumentRepositoryAccess repositoryAccess;

        public OpenAlexResultsTranslator(String groupName, JsonNode contents, DocumentRepositoryAccess repositoryAccess) {
            this.groupName = groupName;
            this.contents = contents;
            this.repositoryAccess = repositoryAccess;
        }

        private DocumentRepositoryQueryResult translate() throws CannotDecodeRepositoryResultsException {
            DocumentRepositoryQueryResult results = new DocumentRepositoryQueryResult();

            // translate result records
            JsonNode resultsNode = contents.get("results");
            if (resultsNode == null) {
                throw new CannotDecodeRepositoryResultsException("missing required node \"results\" in response");
            }

            if (!resultsNode.isArray()) {
                throw new CannotDecodeRepositoryResultsException("\"results\" node is not an array");
            }

            for (JsonNode docRecord : resultsNode) {
                results.getRecordList().add(
                        translateRecord(docRecord));
            }

            return results;
        }

        private DocumentCommonAttributes translateRecord(JsonNode record)
                throws CannotDecodeRepositoryResultsException {
            try {
                URI iduri = URI.create(jsonUtil.getStringNodeValue(record, "id", true));
                String id = iduri.getPath().replaceAll("/", "");
                String doi = jsonUtil.getStringNodeValue(record, "doi", false);

                JsonNode locationListNode = record.get("locations");
                List<String> downloadUrls = new ArrayList<>();
                if (locationListNode != null) {
                    for (JsonNode locationNode : locationListNode) {
                        String pdfUrl = jsonUtil.getStringNodeValue(locationNode, "pdf_url", false);
                        if (pdfUrl != null) {
                            downloadUrls.add(pdfUrl);
                        }
                    }
                }

                JsonNode authorsListNode = record.get("authorships");
                List<String> authors = new ArrayList<>();
                if (authorsListNode != null) {
                    for (JsonNode authorNode : authorsListNode) {
                        JsonNode authorDetailsNode = authorNode.get("author");
                        authors.add(
                                jsonUtil.getStringNodeValue(authorDetailsNode, "display_name", true));
                    }
                }

                JsonNode openAccessNode = record.get("open_access");
                JsonNode primaryLocationNode = record.get("primary_location");
                JsonNode primaryLocationSourceNode = null;
                if (primaryLocationNode != null)
                    primaryLocationSourceNode = primaryLocationNode.get("source");

                List<String> journalTitles = null;
                List<String> issns = null;
                List<String> issns2 = null;

                if (primaryLocationSourceNode != null) {
                    if (primaryLocationSourceNode.has("display_name")) {
                        journalTitles = jsonUtil.getStringArrayNodeValue(
                                primaryLocationSourceNode,
                                "display_name",
                                false,
                                true);
                    }
                    if (primaryLocationSourceNode.has("issn_l")) {
                        issns = jsonUtil.getStringArrayNodeValue(
                                primaryLocationSourceNode,
                                "issn_l",
                                false,
                                true);
                    }
                    if (primaryLocationSourceNode.has("issn")) {
                        issns2 = jsonUtil.getStringArrayNodeValue(
                                primaryLocationSourceNode,
                                "issn",
                                false,
                                true);
                        if (issns != null && issns2.size() > 0) {
                            issns.addAll(issns2);
                        }
                    }
                    if (issns != null) {
                        issns.removeIf(String::isEmpty);
                    }
                }

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
                                        .title(jsonUtil.getStringNodeValue(record, "title", true))
                                        .doi(doi == null ? null : Arrays.asList(doi))
                                        .docAbstract(unwrapAbstractInvertedIndex(record.get("abstract_inverted_index")))
                                        .fullText(null)
                                        .language(jsonUtil.getStringNodeValue(record, "language", false))
                                        .authors(authors)
                                        .contributors(null)
                                        .editors(null)
                                        .citations(null)
                                        .publishedOn(jsonUtil.getDateNodeValue(record, "publication_date", false))
                                        .createdOn(jsonUtil.getDateNodeValue(record, "created_date", false))
                                        .build())
                        .downloadInfo(
                                DocumentCommonAttributes.DownloadInfo.builder()
                                        .documentUrl(downloadUrls)
                                        .minimumRequired(1)
                                        .downloadDriver(DownloadDriverEnum.SIMPLE_DOWNLOADER.name())
                                        .credentials(null)
                                        .build())
                        .publicationDetails(
                                DocumentCommonAttributes.PublicationDetails.builder()
                                        .isOpenAccess(jsonUtil.getBoolNodeValue(openAccessNode, "is_oa", false))
                                        .publicationType(PublicationType
                                                .fromString(jsonUtil.getStringNodeValue(record, "type", false)))
                                        .publicationTypeString(jsonUtil.getStringNodeValue(record, "type", false))
                                        .citationCount(jsonUtil.getIntNodeValue(record, "cited_by_count", false))
                                        .journalTitles(journalTitles)
                                        .journalIssns(issns)
                                        .journalImpactFactor(null)
                                        .build())
                        .rawSearchResultRecord(record.toString())
                        .build();
                return docRecord;
            } catch (UnexpectedJsonStructureException e) {
                throw new CannotDecodeRepositoryResultsException("error decoding Json document record", e);
            }
        }

        private String unwrapAbstractInvertedIndex(JsonNode abstractInvertedIndex)
                throws CannotDecodeRepositoryResultsException {
            Map<Integer, String> abstractIndex = new HashMap<>();

            if (abstractInvertedIndex.isNull()) {
                return null;
            }

            if (!abstractInvertedIndex.isObject()) {
                throw new CannotDecodeRepositoryResultsException(
                        "error decoding abstract inverted index (not an object)");
            }

            Iterator<Map.Entry<String, JsonNode>> it = abstractInvertedIndex.fields();
            while (it.hasNext()) {
                Entry<String, JsonNode> currentWord = it.next();

                if (!currentWord.getValue().isArray()) {
                    throw new CannotDecodeRepositoryResultsException(
                            "error decoding abstract inverted index (not an array)");
                }

                for (JsonNode currentWordPosition : currentWord.getValue()) {
                    abstractIndex.put(currentWordPosition.asInt(), currentWord.getKey());
                }
            }

            StringBuilder sb = new StringBuilder();
            for (String word : abstractIndex.values()) {
                sb.append(word);
                sb.append(' ');
            }

            return sb.toString();
        }
    }
}
