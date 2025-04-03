package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.driver.implementation;

import java.util.List;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
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
import com.exprivia.dfda.tia.model.DocumentCommonAttributes.Document.Citation;
import com.exprivia.dfda.tia.model.KeywordDictionary.KeywordDictionaryDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DocumentRepositoryDriverJrc implements DocumentRepositoryDriverInterface {
    @Autowired
    private JsonUtil jsonUtil;

    @Autowired
    private ObjectMapper mapper;

    private static final String DRIVER_NAME = "jrc";

    @Override
    public String getDriverName() {
        return DRIVER_NAME;
    }

    @Override
    public DocumentRepositoryQueryResult query(String groupName, DocumentRepositoryAccess repoAccess, DocumentRepositoryQuery predicate) 
        throws CannotContactRepositoryException, CannotDecodeRepositoryResultsException {

        JrcQueryTranslator jrcQuery = new JrcQueryTranslator(predicate, repoAccess.getPageLimit());
        String strQuery = jrcQuery.toString();

        log.info("query = {}", strQuery);

        // do the actual query on remote repository
        // https://www.baeldung.com/rest-template

        String repoUrl = repoAccess.getUrl() + "?" + strQuery;
        UriComponents uriComponents = UriComponentsBuilder.fromUriString(repoUrl).build().encode();

        log.debug("encoded url = {}", uriComponents.toUriString());
        
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = null;
        try {
            response = restTemplate.exchange(uriComponents.toUri(), HttpMethod.GET, null, String.class);
        } catch (RestClientException e) {
            throw new CannotContactRepositoryException("cannot contact repository " + repoAccess.getUrl(), e);
        }
        if (response.getStatusCode() != HttpStatus.OK) {
            throw new CannotContactRepositoryException("Error " + response.getStatusCode() + " contacting repo at url " + repoAccess.getUrl().toString());
        }

        try {
            JsonNode root = mapper.readTree(response.getBody());
            log.debug("response json: {}", root);

            /*
			 * parse the query results, extracting the document title,
			 * authors, online url and all the other important metadata
			 */
            JrcResultsTranslator resultTranslator = new JrcResultsTranslator(groupName, root, repoAccess);
            return resultTranslator.translate();
        } catch (JsonProcessingException e) {
            throw new CannotContactRepositoryException("cannot parse json response", e);
        }
	}

    @Override
    public String getQueryAsString(DocumentRepositoryQuery query, int pageLimit) {
        JrcQueryTranslator repoQuery = new JrcQueryTranslator(query, pageLimit);
        return repoQuery.toString();
    }

    /**
     * this class is in charge of translating the DocumentRepositoryQuery
     * into the JRC http query request arguments
     */
    private class JrcQueryTranslator {

        private DocumentRepositoryQuery predicate;
        private Integer pageLimit;

        public JrcQueryTranslator(DocumentRepositoryQuery predicate, Integer pageLimit) {
            this.predicate = predicate;
            this.pageLimit = pageLimit;
        }

        public String toString() {
            StringBuilder s = new StringBuilder();
            s.append("q={\"criteria\":{");

            /* TODO: ensure the result set can be ordered by publication date before enable the pub date condition
            if (predicate.getStartDate() != null) {
                s.append("\"YEAR\":");
                Calendar cal = Calendar.getInstance();
                cal.setTime(predicate.getStartDate());
                s.append(cal.get(Calendar.YEAR));
            }
            */
            s.append("},\"query\":\"");
            s.append(navigateConditions(predicate.getKeywords()).replace("\"", "\\\""));
            s.append("\",\"sort\":\"date-desc\",\"page\":");
            s.append(predicate.getPaginationPageNumber() + 1);
            s.append(",\"pageSize\":");
            if (pageLimit != null) {
                s.append(pageLimit);
            } else {
                s.append(10);
            }
            s.append("}");
            return s.toString();
        }

        private String navigateConditions(KeywordDictionaryDefinition dict) {
            StringBuilder s = new StringBuilder();

            List<String> keywords = dict.getKeywords();

            if (dict.isSkipKeywords() == false) {
                int j = 0;
                if (keywords.size() > 1) s.append("(");
                for (String keyword : dict.getKeywords()) {
                    s.append("\"" + keyword + "\"");

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

	private class JrcResultsTranslator {
        private String groupName;
		private JsonNode contents;
        private DocumentRepositoryAccess repositoryAccess;

        private static final String TYPE_DOCUMENT = "DOCUMENT";

		public JrcResultsTranslator(String groupName, JsonNode contents, DocumentRepositoryAccess repositoryAccess) {
            this.groupName = groupName;
			this.contents = contents;
            this.repositoryAccess = repositoryAccess;
		}

		private DocumentRepositoryQueryResult translate() throws CannotDecodeRepositoryResultsException {
            DocumentRepositoryQueryResult results = new DocumentRepositoryQueryResult();

            // translate result records
            JsonNode items = contents.get("items");
            if (items != null) {
                if (!items.isArray()) {
                    throw new CannotDecodeRepositoryResultsException("\"items\" node is not an array");
				}

				for (JsonNode docRecord : items) {
                    DocumentCommonAttributes docAttr = translateRecord(docRecord);
					
                    if (docAttr != null) {
                        results.getRecordList().add(docAttr);
                    }
				}
            }

            return results;
		}

        private DocumentCommonAttributes translateRecord(JsonNode record) throws CannotDecodeRepositoryResultsException {
            /*
            @NonNull
            idInRepo            id
            @NonNull
            repositoryUrl       (source repository url)
            doi                 dc.identifier.doi (can be a list)
            @NonNull
            title               dc.title
            abstrct             dc.description.abstract
            language            dc.language
            @NonNull
            documentUrl         dc.identifier.uri
            contributors        search.contributors
            editors             dc.contributor.editor
            createdOn           dc.date.created
            publishedOn         dc.date.available
            @NonNull
            rawSearchResultRecord (the whole record text)
            */
            try {
                String id = jsonUtil.getStringNodeValue(record, "id", true);

                List<String> dois = new ArrayList<>();
                for (String doi : jsonUtil.getStringArrayNodeValue(record, "dc.identifier.doi", false, true)) {
                    dois.add(removeMedium(doi));
                }
                
                List<String> issns = new ArrayList<>();
                for (String issn : jsonUtil.getStringArrayNodeValue(record, "dc.identifier.issn", false, true)) {
                    issns.add(removeMedium(issn));
                }
                issns.removeIf(String::isEmpty);

                String publicationTypeString = jsonUtil.getStringNodeValue(record, "dc.type", false);
                List<String> downloadUrlList = resolveDownloadUrl(record);
                if (downloadUrlList == null) {
                    log.warn("document {} does not have download-urls, skipping...", id);
                    return null;
                }

                DocumentCommonAttributes docRecord = DocumentCommonAttributes.builder()
                    .id(id)
                    .group(groupName)
                    .repository(
                        DocumentCommonAttributes.Repository.builder()
                            .driver(getDriverName())
                            .url(repositoryAccess.getUrl().toString())
                            .credentials(null)
                            .build())
                    .document(
                        DocumentCommonAttributes.Document.builder()
                        .title(jsonUtil.getStringNodeValue(record, "dc.title", true))
                        .doi(dois)
                        .docAbstract(jsonUtil.getStringNodeValue(record, "dc.description.abstract", false))
                        .language(jsonUtil.getStringNodeValue(record, "dc.language", false))
                        .authors(jsonUtil.getStringArrayNodeValue(record, "dc.contributor.author", false, true))
                        .contributors(jsonUtil.getStringArrayNodeValue(record, "search.contributors", false, true))
                        .editors(jsonUtil.getStringArrayNodeValue(record, "dc.contributor.editor", false, true))
                        .citations(resolveCitations(record))
                        .build()
                        )
                    .downloadInfo(
                        DocumentCommonAttributes.DownloadInfo.builder()
                        .documentUrl(downloadUrlList)
                        .minimumRequired(1)
                        .downloadDriver(DownloadDriverEnum.SIMPLE_DOWNLOADER.name())
                        .credentials(null)
                        .build()
                    )
                    .publicationDetails(
                        DocumentCommonAttributes.PublicationDetails.builder()
                        .isOpenAccess(null)
                        .publicationType(PublicationType.fromString(publicationTypeString))
                        .publicationTypeString(publicationTypeString)
                        .citationCount(null)
                        .journalTitles(null)
                        .journalIssns(issns == null ? null : issns)
                        .journalImpactFactor(null)
                        .build()
                    )
                    .rawSearchResultRecord(record.toString())
                    .build();
                

                docRecord.getDocument().setCreatedOn(jsonUtil.getDateNodeValue(record, "dc.date.created", false));
                docRecord.getDocument().setPublishedOn(jsonUtil.getDateNodeValue(record, "dc.date.available", false));
            
                return docRecord;
            } catch (UnexpectedJsonStructureException e) {
                throw new CannotDecodeRepositoryResultsException("error decoding JRC Json document record", e);
            }
        }

        private List<String> resolveDownloadUrl(JsonNode record) throws UnexpectedJsonStructureException {
            List<String> urls = new ArrayList<>();

            List<String> baseUrlList = jsonUtil.getStringArrayNodeValue(record, "dc.identifier.uri", true, true);
            String baseUrl = baseUrlList.get(0).replaceAll("/handle.*", "");

            List<String> ids = jsonUtil.getStringArrayNodeValue(record, "document.id", false, true);
            if (ids == null) {
                return null;
            }

            for (String id: ids) {
                String docId = "document." + id.trim();
                String docType = jsonUtil.getStringNodeValue(record, docId + ".type", true);

                if (docType.equals(TYPE_DOCUMENT)) {
                    String fname = jsonUtil.getStringNodeValue(record, docId + ".filename", true);
                    String url = baseUrl + "/bitstream/" + jsonUtil.getStringNodeValue(record, "id", true) + "/" + fname;
                    
                    log.debug("found pdf URL {} for doc id {}", url, id);
                    urls.add(url);
                }
            }

            return urls;
        }

        private List<Citation> resolveCitations(JsonNode record) throws UnexpectedJsonStructureException {
            List<Citation> citations = new ArrayList<>();

            List<String> ids = jsonUtil.getStringArrayNodeValue(record, "data.id.citations", true, true);

            for (String id: ids) {
                String citId = "data.citation." + id.trim();
                String citType = jsonUtil.getStringNodeValue(record, citId + ".type", true);
                String citLang = jsonUtil.getStringNodeValue(record, citId + ".language", true);
                String citText = jsonUtil.getStringNodeValue(record, citId + ".text", true);

                citations.add(
                    Citation.builder()
                    .type(citType)
                    .language(citLang)
                    .text(citText)
                    .build());
            }

            return citations;
        }

        private static String removeMedium(String value) {
            return value
                .replace("(online)", "")
                .replace("(print)", "")
                .trim();
        }

	}
}
