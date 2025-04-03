package com.exprivia.dfda.duatiadocumentretriever;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

import com.exprivia.dfda.duatiadocumentretriever.business.DocumentRetriever;
import com.exprivia.dfda.duatiadocumentretriever.business.DocumentRetrieverDriverTest;
import com.exprivia.dfda.duatiadocumentretriever.business.DuaTiaDocumentRetrieverDocStatusHelper;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.download.DocumentDownloadAction;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.download.DocumentDownloadResult;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions.CannotContactDocumentRepositoryException;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions.CannotDownloadDocumentException;
import com.exprivia.dfda.duatiadocumentretriever.business.docretriever.exceptions.CannotWriteDocumentException;
import com.exprivia.dfda.tia.model.DocumentCommonAttributes;
import com.exprivia.dfda.tia.service.DocumentQueueService;
import com.exprivia.dfda.tia.service.exception.DocumentQueueException;

import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@ComponentScan(basePackageClasses = { DuaTiaDocumentRetrieverApplication.class, DocumentQueueService.class } )
@EnableRedisRepositories({"com.exprivia.dfda.duatiadocumentretriever", "com.exprivia.dfda.tia"})
@Slf4j
public class DuaTiaDocumentRetrieverApplication implements CommandLineRunner {
	@Autowired
	private DocumentQueueService documentQueueService;

	@Autowired
	private DocumentRetriever documentRetriever;

	@Autowired
	private DuaTiaDocumentRetrieverConfiguration config;

	@Autowired
	private DuaTiaDocumentRetrieverDocStatusHelper docStatusHelper;

	@Autowired
	private DocumentRetrieverDriverTest documentRetrieverDriverTest;

	private DocumentRetrieverRunMode runMode = DocumentRetrieverRunMode.NORMAL;

	DocumentDownloadAction testDownloadAction = null;
	QueryFilter queryFilter = QueryFilter.ALL;
	UpdateTarget updateTarget = UpdateTarget.RESET_RETRY_COUNTER;

	public static void main(String[] args) {
		SpringApplication.run(DuaTiaDocumentRetrieverApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		parseArguments(args);

		switch (runMode) {
			case TEST:
				testMode();
				break;

			case QUERY:
				queryMode();
				break;
			
			case UPDATE:
				updateMode();
				break;

			default:
				daemonMode();
				break;
		}
	}

	private void parseArguments(String... args) {
		String documentId = null;
		String driver = null;
		List<String> doiList = new ArrayList<>();
		List<String> urlList = new ArrayList<>();
		String credentials = null;
		String path = null;
		String group = "test-group";
		
		for (int a = 0; a < args.length; a++) {
			if (args[a].equals("-t")) runMode = DocumentRetrieverRunMode.TEST;
			else if (args[a].equals("-q")) runMode = DocumentRetrieverRunMode.QUERY;
			else if (args[a].equals("-U")) runMode = DocumentRetrieverRunMode.UPDATE;
			else if (args[a].equals("-i") && a + 1 < args.length) documentId = args[a + 1];
			else if (args[a].equals("-d") && a + 1 < args.length) driver = args[a + 1];
			else if (args[a].equals("-D") && a + 1 < args.length) doiList.add(args[a + 1]);
			else if (args[a].equals("-u") && a + 1 < args.length) urlList.add(args[a + 1]);
			else if (args[a].equals("-c") && a + 1 < args.length) credentials = args[a + 1];
			else if (args[a].equals("-p") && a + 1 < args.length) path = args[a + 1];
			else if (args[a].equals("-f") && a + 1 < args.length) queryFilter = QueryFilter.valueOf(args[a + 1]);
			else if (args[a].equals("-T") && a + 1 < args.length) updateTarget = UpdateTarget.valueOf(args[a + 1]);
			else if (args[a].equals("-g") && a + 1 < args.length) group = args[a + 1];
		}
		if (runMode == DocumentRetrieverRunMode.TEST) {
			testDownloadAction = new DocumentDownloadAction(
				group,
				documentId, 
				driver, 
				doiList,
				urlList, 
				1, 
				credentials, 
				path);
		}
	}

	private void daemonMode() throws InterruptedException {
		while (true) {
			try {
				DocumentCommonAttributes doc = documentQueueService.popDocumentEvent();

				if (doc == null) {
					Thread.sleep(10000);
				} else {
					this.handleMessage(doc);
				}

			} catch (DocumentQueueException e) {
				log.error("cannot get event from repository queue", e);
				Thread.sleep(20000);
			}
		}
	}

	private void handleMessage(DocumentCommonAttributes docAttr) {
		log.debug("received message {}", docAttr);

        try {
            DocumentCommonAttributes.DownloadInfo downloadInfo = docAttr.getDownloadInfo();

			Integer minimumRequired = downloadInfo.getMinimumRequired();
			if (minimumRequired == null) {
				minimumRequired = downloadInfo.getDocumentUrl().size();
			}

			DocumentDownloadAction download = new DocumentDownloadAction(
				docAttr.getGroup(),
                docAttr.getId(),
                downloadInfo.getDownloadDriver(),
				docAttr.getDocument().getDoi(),
                downloadInfo.getDocumentUrl(),
                minimumRequired,
                downloadInfo.getCredentials(),
				config.getDownloadPath(),
				docAttr.getRepository().getDriver(),
				docAttr.getDocument().getKeywordDictionarySecondLevel());

            DocumentDownloadResult result = documentRetriever.download(download, docAttr);

            log.info("download result: {}", result);

        } catch (CannotContactDocumentRepositoryException | CannotDownloadDocumentException e) {
            log.error("error downloading document \"" + docAttr.getId() + "\" from " + docAttr.getDownloadInfo().getDocumentUrl(), e);
        } catch (CannotWriteDocumentException e) {
            log.error("cannot write document \"" + docAttr.getId() + "\" data to the local folder \"" + config.getDownloadPath() + "\"", e);
        } catch (Exception e) {
            log.error("cannot process event: " + e.getMessage(), e);
        }
	}

	private void testMode() {
		try {
			documentRetrieverDriverTest.testDownload(testDownloadAction);
		} catch (CannotContactDocumentRepositoryException | CannotDownloadDocumentException e) {
            log.error("error downloading document \"" + testDownloadAction.getId() + "\" from " + testDownloadAction.getDocumentUrl().get(0), e);
        } catch (Exception e) {
            log.error("cannot process event: " + e.getMessage(), e);
        }
	}

	private void queryMode() {
		switch (queryFilter) {
			case COMPLETED:
				docStatusHelper.query(true, false, false);
				break;
			case RETRYING:
				docStatusHelper.query(false, true, false);
				break;
			case FAILED:
				docStatusHelper.query(false, false, true);
				break;
			case ALL:
				docStatusHelper.query(true, true, true);
				break;
		}
	}

	private void updateMode() {
		switch (updateTarget) {
			case RESET_RETRY_COUNTER:
				docStatusHelper.resetRetryCountForFailedFiles();
				break;
		}
	}

	private enum DocumentRetrieverRunMode {
		NORMAL,
		TEST,
		QUERY,
		UPDATE
	}

	private enum QueryFilter {
		COMPLETED,
		FAILED,
		RETRYING,
		ALL
	}

	private enum UpdateTarget {
		RESET_RETRY_COUNTER
	}
}
