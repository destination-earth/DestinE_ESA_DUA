package com.exprivia.dfda.duatiarepositoryharvester.business;

import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.DocumentRepositoryAccess;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.DocumentRepositoryDriverFactory;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.driver.DocumentRepositoryDriverInterface;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.BadRepositoryConfigurationException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.CannotContactRepositoryException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.CannotDecodeRepositoryResultsException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.query.DocumentRepositoryQuery;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.result.DocumentRepositoryQueryResult;
import com.exprivia.dfda.tia.model.DocumentCommonAttributes;
import com.exprivia.dfda.tia.model.DocumentRepository;
import com.exprivia.dfda.tia.model.KeywordDictionary;
import com.exprivia.dfda.tia.model.KeywordDictionary.KeywordDictionaryDefinition;
import com.exprivia.dfda.tia.model.DocumentCommonAttributes.PublicationType;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RepositoryReporter {
    @Autowired
    private DocumentRepositoryDriverFactory drFactory;

    @Autowired
    private KeywordDictionary dictionary;

    public void searchRepository(DocumentRepository docRepo, PrintWriter out, boolean verbose) 
        throws BadRepositoryConfigurationException, CannotContactRepositoryException, CannotDecodeRepositoryResultsException {
        log.info("searching repository \"{}\"", docRepo.getName());

        // prepare communication driver and query conditions
        DocumentRepositoryDriverInterface driver = drFactory.factory(docRepo.getDriver());

        // setup start date
        Timestamp startDate = docRepo.getLastVisit();

        DocumentRepositoryAccess repoAccess = DocumentRepositoryAccess.createFromDocumentRepositoryDefinition(docRepo);
        KeywordDictionaryDefinition dictionaryDefinition = dictionary.getDictionaryDefinition();

        int grandTotal = 0;
        Integer initialPageDelay = docRepo.getPauseBetweenPages();
        for (int i = 0; i < dictionaryDefinition.getChildren().size(); i++) {
            KeywordDictionaryDefinition dictionary = new KeywordDictionaryDefinition(
                dictionaryDefinition.getKeywords(), 
                dictionaryDefinition.isSkipKeywords(),
                Arrays.asList(dictionaryDefinition.getChildren().get(i)));

            KeywordDictionaryDefinition secondLevelKeyword = dictionaryDefinition.getChildren().get(i);
            DocumentRepositoryQuery query = new DocumentRepositoryQuery(
                dictionary, 
                startDate, 0, 0);


            // do the harvesting
            String missionName =  secondLevelKeyword.getKeywords().get(0);
            docRepo.setPauseBetweenPages(initialPageDelay); // reset the initial page delay

            String queryStr = driver.getQueryAsString(query, 0);
            out.println(
                String.format("%s,%s,,,,\"%s\"", 
                    docRepo.getName(), 
                    missionName, 
                    queryStr.replaceAll("\"", "\\\\\"")));

            int resultsCount = searchMission(driver, repoAccess, query, missionName, out, verbose);
            grandTotal += resultsCount;

            log.info("repository \"{}\", mission \"{}\", documents found: {}", docRepo.getName(), missionName, resultsCount);

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

        log.info("repository \"{}\" visit finished, documents found: {}", docRepo.getName(), grandTotal);
    }

    protected int searchMission(
        DocumentRepositoryDriverInterface driver, 
        DocumentRepositoryAccess repoAccess,
        DocumentRepositoryQuery predicate,
        String secondLevelKeyword, PrintWriter out, boolean verbose) 
        throws CannotContactRepositoryException, CannotDecodeRepositoryResultsException {

        // 1. query repository with pagination
        driver.initializeDriver();

        DocumentRepositoryQueryResult results = null;
        int page = 0;
        int totalResults = 0;
        boolean continuePagination = true;
        do {
            results = driver.query("reporter", repoAccess, predicate);

            log.debug("query results: {}", results);
            log.info("found {} results", results.getRecordList().size());

            // 2. increase the pagination cursor
            page++;
            totalResults += results.getRecordList().size();
            predicate.setPaginationPageNumber(page);
            predicate.setPaginationRecordOffset(totalResults);

            // 3. report found doc info
            for (DocumentCommonAttributes doc : results.getRecordList()) {
                String title = sanitizeStringForCSV(doc.getDocument().getTitle());
                if (!verbose) {
                    out.println(String.format("%s,%s,%s,\"%s\",%s,\"%s\"", 
                        driver.getDriverName(), 
                        secondLevelKeyword,
                        doc.getId(),
                        doc.getDocument().getDoi(),
                        doc.getDocument().getLanguage(),
                        title));
                } else {
                    PublicationType pt = doc.getPublicationDetails().getPublicationType();
                    List<String> journalTitles = doc.getPublicationDetails().getJournalTitles();
                    List<String> journalIssns = doc.getPublicationDetails().getJournalIssns();

                    String abstrct = sanitizeStringForCSV(doc.getDocument().getDocAbstract());

                    out.println(String.format("%s,%s,%s,\"%s\",%s,\"%s\",\"%s\",%s,\"%s\",\"%s\",%d,\"%s\",\"%s\",%.2f,%.2f", 
                        driver.getDriverName(), 
                        secondLevelKeyword,
                        doc.getId(),
                        doc.getDocument().getDoi(),
                        doc.getDocument().getLanguage(),
                        title,
                        sanitizeStringForCSV(abstrct),
                        doc.getPublicationDetails().getIsOpenAccess(),
                        pt == null ? null : pt.name(),
                        doc.getPublicationDetails().getPublicationTypeString(),
                        doc.getPublicationDetails().getCitationCount(),
                        journalTitles == null ? null : String.join(" / ", journalTitles),
                        journalIssns == null ? null : String.join(" / ",  journalIssns),
                        doc.getPublicationDetails().getJournalImpactFactor(),
                        doc.getPublicationDetails().getRepositoryScore())
                        );
                }
            }

            if (driver.customPaginationImplemention()) {
                continuePagination = driver.proceedWithQueryPage();
            } else {
                continuePagination = !results.getRecordList().isEmpty();
            }

            if (continuePagination && repoAccess.getPauseBetweenPages() != null) {
                log.info("pausing pagination for {} seconds", repoAccess.getPauseBetweenPages());
                try {
                    Thread.sleep(repoAccess.getPauseBetweenPages() * 1000L);
                } catch (InterruptedException e) {
                    log.warn("pause interrupted by: " + e.getMessage());
                }
            }
        } while (continuePagination);
        
        return totalResults;
    }

    private static String sanitizeStringForCSV(String val) {
        if (val == null) return null;
        return val.replaceAll("\n", " ").replaceAll("\"", "\\\\\"");
    }

}
