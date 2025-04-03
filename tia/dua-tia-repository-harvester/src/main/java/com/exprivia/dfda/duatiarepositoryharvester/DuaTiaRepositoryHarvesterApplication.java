package com.exprivia.dfda.duatiarepositoryharvester;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

import com.exprivia.dfda.duatiarepositoryharvester.DuaTiaRepositoryHarvesterConfiguration.QueryModeRepository;
import com.exprivia.dfda.duatiarepositoryharvester.business.RepositoryHarvester;
import com.exprivia.dfda.duatiarepositoryharvester.business.RepositoryReporter;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.DocumentRepositoryDriverFactory;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.driver.DocumentRepositoryDriverInterface;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.BadRepositoryConfigurationException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.CannotContactRepositoryException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.CannotDecodeRepositoryResultsException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.query.DocumentRepositoryQuery;
import com.exprivia.dfda.tia.model.DocumentRepository;
import com.exprivia.dfda.tia.model.KeywordDictionary;
import com.exprivia.dfda.tia.model.KeywordDictionary.KeywordDictionaryDefinition;
import com.exprivia.dfda.tia.service.RepositoryQueueService;
import com.exprivia.dfda.tia.service.exception.RepositoryQueueException;

import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@ComponentScan({"com.exprivia.dfda.duatiarepositoryharvester", "com.exprivia.dfda.tia"})
@EnableRedisRepositories({"com.exprivia.dfda.duatiarepositoryharvester", "com.exprivia.dfda.tia"})
@Slf4j
public class DuaTiaRepositoryHarvesterApplication implements CommandLineRunner {
	@Autowired
	private RepositoryQueueService repositoryQueueService;

    @Autowired
    private RepositoryHarvester harvester;

	// print mode
	@Autowired
	KeywordDictionary dictionary;
	
	@Autowired
    private DocumentRepositoryDriverFactory drFactory;
	
	// query mode
	@Autowired 
	private RepositoryReporter repositoryReporter;
	
	@Autowired
	private DuaTiaRepositoryHarvesterConfiguration config;

	public static void main(String[] args) {
		SpringApplication.run(DuaTiaRepositoryHarvesterApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		RepositoryHarvesterMode mode = RepositoryHarvesterMode.MODE_DAEMON;
		boolean verbose = false;
		
		for (String arg : args) {
			if (arg.equals("-d")) mode = RepositoryHarvesterMode.MODE_DAEMON;
			if (arg.equals("-p")) mode = RepositoryHarvesterMode.MODE_PRINT_REPO;
			if (arg.equals("-q")) mode = RepositoryHarvesterMode.MODE_QUERY_REPO;
			if (arg.equals("-v")) verbose = true;
		}

		switch (mode) {
			case MODE_DAEMON:
				log.info("starting in daemon mode");
				daemonMode();
				break;
			case MODE_PRINT_REPO:
				log.info("starting in print repo query mode");
				printRepo();
				break;
			case MODE_QUERY_REPO:
				log.info("starting in repo query mode");
				queryRepo(verbose);
				break;
			default:
				daemonMode();
		}
		
	}

	private void daemonMode() throws Exception {
		while (true) {
			try {
				DocumentRepository docRepo = repositoryQueueService.popRepositoryEvent();

				if (docRepo == null) {
					Thread.sleep(10000);
				} else {
					harvester.visitRepository(docRepo);
				}

			} catch (RepositoryQueueException e) {
				log.error("cannot get event from repository queue", e);
				Thread.sleep(20000);
			} catch (BadRepositoryConfigurationException | CannotContactRepositoryException | CannotDecodeRepositoryResultsException e) {
				log.error("cannot process repository visit", e);
			}
		}
	}

	private void printRepo() {
		String [] drivers = { "jrc", "core", "scopus", "semantic_scholar", "open_alex", "nature", "overton" };

		for (KeywordDictionaryDefinition secondLevelKeyword : dictionary.getDictionaryDefinition().getChildren()) {
			DocumentRepositoryQuery query = new DocumentRepositoryQuery(
				secondLevelKeyword, 
				null, 0, 0);

			for (String driverName : drivers) {
				DocumentRepositoryDriverInterface driver = drFactory.factory(driverName);
				driver.initializeDriver();
				log.info("--- mission: {}, repo: {} ---", secondLevelKeyword.getKeywords().get(0), driverName);
				log.info(driver.getQueryAsString(query, 10));
				log.info("----- ");
			}
		}

	}

	private void queryRepo(boolean verbose) throws FileNotFoundException {
		PrintWriter out = new PrintWriter(config.getQueryModeOutputFilename());

        // write header
        out.println(
            String.format(
                "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s", 
                "repository", 
                "mission", 
                "doc_id", 
                "dois", 
                "language", 
                "title", 
				"abstract",
                "open_access", 
                "type", 
                "type_string", 
                "cited_by_count", 
                "publication_titles", 
                "publication_issns",
				"journal_impact_factor",
				"repo_score")
            );
		
		for (QueryModeRepository qmRepo : config.getQueryModeRepositories()) {
			DocumentRepository docRepo = DocumentRepository.builder()
				.name(qmRepo.getDriver())
				.driver(qmRepo.getDriver())
				.url(qmRepo.getUrl())
				.credentials(qmRepo.getCredentials())
				.frequency(60).enabled(true)
				.pauseBetweenPages(qmRepo.getPageDelay())
				.pageLimit(qmRepo.getPageSize())
				.build();
			try {
				repositoryReporter.searchRepository(docRepo, out, verbose);
			} catch (CannotContactRepositoryException e) {
				log.error("cannot contact repository " + docRepo.getName() + ": " + e.getMessage());
				e.printStackTrace(out);
				e.printStackTrace();
			} catch (BadRepositoryConfigurationException e) {
				e.printStackTrace(out);
				e.printStackTrace();
				out.close();
				throw new RuntimeException("cannot visit repository " + docRepo.getName() + ": " + e.getMessage(), e);
			} catch (CannotDecodeRepositoryResultsException e) {
				log.error("cannot decode repository " + docRepo.getName() + " results: " + e.getMessage());
				e.printStackTrace(out);
				e.printStackTrace();
			}
		}

		out.close();
		log.info("written " + config.getQueryModeOutputFilename());
	}

	private enum RepositoryHarvesterMode {
		MODE_DAEMON,
		MODE_PRINT_REPO,
		MODE_QUERY_REPO
	}

}
