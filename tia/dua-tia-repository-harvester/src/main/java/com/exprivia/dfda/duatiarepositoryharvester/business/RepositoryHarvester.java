package com.exprivia.dfda.duatiarepositoryharvester.business;

import java.util.Arrays;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import com.exprivia.dfda.duatiarepositoryharvester.DuaTiaRepositoryHarvesterConfiguration;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.DocumentRepositoryAccess;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.DocumentRepositoryDriverFactory;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.driver.DocumentRepositoryDriverInterface;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.BadRepositoryConfigurationException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.CannotContactRepositoryException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.CannotDecodeRepositoryResultsException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.query.DocumentRepositoryQuery;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.result.DocumentRepositoryQueryResult;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.util.DateUtil;
import com.exprivia.dfda.tia.model.DocumentCommonAttributes;
import com.exprivia.dfda.tia.model.DocumentRepository;
import com.exprivia.dfda.tia.model.DocumentStatus;
import com.exprivia.dfda.tia.model.KeywordDictionary;
import com.exprivia.dfda.tia.model.QueryMaxPublicationDate;
import com.exprivia.dfda.tia.model.RepositorySearchBinding;
import com.exprivia.dfda.tia.model.DocumentCommonAttributes.SearchConfiguration;
import com.exprivia.dfda.tia.model.KeywordDictionary.KeywordDictionaryDefinition;
import com.exprivia.dfda.tia.service.DocumentQueueService;
import com.exprivia.dfda.tia.service.DocumentStatusService;
import com.exprivia.dfda.tia.service.exception.DocumentQueueException;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RepositoryHarvester {
    @Autowired
    private DocumentRepositoryDriverFactory drFactory;

    @Autowired
    private DocumentQueueService documentQueueService;

    @Autowired
    private DocumentStatusService documentStatusService;

    @Autowired
    private DuaTiaRepositoryHarvesterConfiguration configuration;

    @Autowired
    private DateUtil dateUtil;


    public void visitRepository(DocumentRepository docRepo) 
        throws BadRepositoryConfigurationException, CannotContactRepositoryException, CannotDecodeRepositoryResultsException {

        log.info("starting repository visit with \"{}\"", docRepo.getName());

        // 1. call the repository-search-bindings/by-doc-repo to check which initiative/doctype/kwdict must be used for this visit
        // http://localhost:8080/tia/api/v1/repository-search-bindings/by-doc-repo/{repo-id}
        for (RepositorySearchBinding searchBinding : configuration.getRepositorySearchBindingsByRepositoryId(docRepo.getId())) {
            log.info("current repository search: {}", searchBinding);

            // 2. get the keyword dictionary by id
            // http://localhost:8080/tia/api/v1/keyword-dictionaries/{kwdict-id}
            KeywordDictionary keywordDictionary = configuration.getKeywordDictionary(searchBinding.getKeywordDictionaryId());

            // 3. get the main working directory acronym
            // http://localhost:8080/tia/api/v1/repository-search-bindings/{binding-id}/acronym
            String groupAcronym = configuration.getSearchGroupAcronymByBindingId(searchBinding.getId());

            log.info("visiting repository '{}' with keyword dictionary '{}' and main workdir '{}'", 
                docRepo.getName(), 
                keywordDictionary.getName(),
                groupAcronym);

            visitRepository(searchBinding, docRepo, keywordDictionary, groupAcronym);
        }
    }

    private void visitRepository(
        RepositorySearchBinding searchBinding,
        DocumentRepository docRepo, 
        KeywordDictionary dictionary, 
        String groupAcronym) 
        throws BadRepositoryConfigurationException, CannotContactRepositoryException, CannotDecodeRepositoryResultsException {

        SearchConfiguration searchConfiguration = new DocumentCommonAttributes.SearchConfiguration(
            searchBinding.getInitiativeId(),
            searchBinding.getDocumentTypeId(),
            searchBinding.getKeywordDictionaryId()
        );

        // prepare communication driver and query conditions
        DocumentRepositoryDriverInterface driver = drFactory.factory(docRepo.getDriver());

        DocumentRepositoryAccess repoAccess = DocumentRepositoryAccess.createFromDocumentRepositoryDefinition(docRepo);

        int grandTotal = 0;
        Integer initialPageDelay = docRepo.getPauseBetweenPages();
        KeywordDictionaryDefinition dictionaryDefinition = dictionary.getDictionaryDefinition();

        for (int i = 0; i < dictionaryDefinition.getChildren().size(); i++) {
            KeywordDictionaryDefinition currentDictionary = new KeywordDictionaryDefinition(
                dictionaryDefinition.getKeywords(), 
                dictionaryDefinition.isSkipKeywords(),
                Arrays.asList(dictionaryDefinition.getChildren().get(i)));

            KeywordDictionaryDefinition secondLevelKeyword = dictionaryDefinition.getChildren().get(i);
    
            String missionName = secondLevelKeyword.getKeywords().get(0);
            docRepo.setPauseBetweenPages(initialPageDelay); // reset the initial page delay

            // setup the query
            Date startDate = getQueryMaxPublicationDate(searchBinding.getId(), docRepo.getId(), missionName);
            DocumentRepositoryQuery query = new DocumentRepositoryQuery(
                currentDictionary, 
                startDate, 0, 0);

            // do the harvesting
            HarvestingResult harvestingResult = processHarvesting(driver, repoAccess, query, missionName, groupAcronym, searchConfiguration);
            grandTotal += harvestingResult.getResultCount();

            log.info("repository \"{}\", mission \"{}\", start-date \"{}\", documents found: {}, max pub date retrieved: \"{}\"", 
                docRepo.getName(), 
                missionName, 
                dateUtil.formatYyyyMmDd(startDate),
                harvestingResult.getResultCount(),
                dateUtil.formatYyyyMmDd(harvestingResult.getMaxPublicationDate()));

            if (harvestingResult.getMaxPublicationDate() != null) {
                // update max publication date
                setQueryMaxPublicationDate(
                    searchBinding.getId(), 
                    docRepo.getId(), 
                    missionName, 
                    harvestingResult.getMaxPublicationDate());
            }

            // pause between missions scan on the same repo
            if (i < dictionaryDefinition.getChildren().size() - 1 && repoAccess.getPauseBetweenPages() != null) {
                log.info("pausing between repo missions scan for {} seconds", repoAccess.getPauseBetweenPages());
                try {
                    Thread.sleep(repoAccess.getPauseBetweenPages() * 1000L);
                } catch (InterruptedException e) {
                    log.warn("pause interrupted by: " + e.getMessage());
                }
            }
        }

        log.info("repository \"{}\" visit for group \"{}\" finished, documents found: {}", docRepo.getName(), groupAcronym, grandTotal);
    }

    protected HarvestingResult processHarvesting(
        DocumentRepositoryDriverInterface driver, 
        DocumentRepositoryAccess repoAccess,
        DocumentRepositoryQuery predicate,
        String secondLevelKeyword,
        String groupAcronym,
        SearchConfiguration searchConfiguration) 
        throws CannotContactRepositoryException, CannotDecodeRepositoryResultsException {

        // 1. query repository with pagination
        driver.initializeDriver();

        DocumentRepositoryQueryResult results = null;
        int page = 0;
        int totalResults = 0;
        Date maxPublicationDate = null;
        boolean continuePagination = true;
        do {
            results = driver.query(groupAcronym, repoAccess, predicate);

            log.debug("query results: {}", results);
            log.info("found {} results", results.getRecordList().size());

            // 2. increase the pagination cursor
            page++;
            totalResults += results.getRecordList().size();
            predicate.setPaginationPageNumber(page);
            predicate.setPaginationRecordOffset(totalResults);

            // 3. generate queue messages to propagate download requests to the appropriate agents
            for (DocumentCommonAttributes doc : results.getRecordList()) {

                doc.getDocument().setKeywordDictionarySecondLevel(secondLevelKeyword);
                doc.setGroup(groupAcronym);
                doc.setConfiguration(searchConfiguration);

                // 4. check if the document has been already downloaded
                DocumentStatus docStat = documentStatusService.getEntity(
                    groupAcronym,
                    doc.getRepository().getDriver(), 
                    doc.getDocument().getKeywordDictionarySecondLevel(),
                    doc.getId());

                if (docStat != null && (docStat.isCompleted() || docStat.getRetryCount() == 0)) {
                    log.info("document {} {} has been previously downloaded or has reached maximum number of retries, skipping ({})", 
                        doc.getRepository().getDriver(),
                        doc.getId(),
                        docStat);
                } else {
                    try {
                        // 5. propagate the event to the "docs" download queue
                        documentQueueService.pushDocumentEvent(doc);
                    } catch (DocumentQueueException e) {
                        log.error("cannot push document event to queue", e);
                    }
                }

                // 6. set the maximum publication date for the next query
                if (maxPublicationDate == null || 
                    (  doc.getDocument().getPublishedOn() != null && 
                       maxPublicationDate.before(doc.getDocument().getPublishedOn()))) {
                    maxPublicationDate = doc.getDocument().getPublishedOn();
                }
            }

            if (driver.customPaginationImplemention()) {
                continuePagination = driver.proceedWithQueryPage();
            } else {
                continuePagination = !results.getRecordList().isEmpty();
            }

            // TODO: check the pause for the same repo on different repository-search-binding
            // if (continuePagination && repoAccess.getPauseBetweenPages() != null) {
            //      log.info("pausing pagination for {} seconds", repoAccess.getPauseBetweenPages());
            if (repoAccess.getPauseBetweenPages() != null) {
                log.info("pausing for {} seconds", repoAccess.getPauseBetweenPages());
                try {
                    Thread.sleep(repoAccess.getPauseBetweenPages() * 1000L);
                } catch (InterruptedException e) {
                    log.warn("pause interrupted by: " + e.getMessage());
                }
            }
        } while (continuePagination);
        
        return new HarvestingResult(totalResults, maxPublicationDate);
    }

    private Date getQueryMaxPublicationDate(String repositorySearchBindingId, String repositoryId, String mission) {
        // get the max publication date reached for this repoSearchBinding/repository combination
        // http://localhost:8080/tia/api/v1/query-max-publication-date/{rsbinding-id}/{repo-id}/{mission}
        Date maxPublicationDate = null;
        try {
            QueryMaxPublicationDate queryMaxPublicationDate = configuration.getQueryMaxPublicationDate(
                repositorySearchBindingId, 
                repositoryId,
                mission);
            maxPublicationDate = queryMaxPublicationDate.getMaxPublicationDate();
            log.info("get max publication date for {}/{}/{}: {}", 
                repositorySearchBindingId, 
                repositoryId,
                mission,
                dateUtil.formatYyyyMmDd(maxPublicationDate));
        } catch (HttpClientErrorException e) {
            // permit only 404 not found status code
            if (!e.getStatusCode().equals(HttpStatusCode.valueOf(404))) {
                throw e;
            }
            log.warn("query max publication date not found");
        }
        return maxPublicationDate;
    }

    private void setQueryMaxPublicationDate(
        String repositorySearchBindingId, 
        String repositoryId, 
        String mission, 
        Date maxPublicationDate) {
        
        log.info("set max publication date for {}/{}/{}: {}", 
            repositorySearchBindingId, 
            repositoryId,
            mission,
            dateUtil.formatYyyyMmDd(maxPublicationDate));

        configuration.setQueryMaxPublicationDate(repositorySearchBindingId, repositoryId, mission, maxPublicationDate);
    }

    @Data
    @AllArgsConstructor
    static private class HarvestingResult {
        private int resultCount;
        private Date maxPublicationDate;
    }
}
