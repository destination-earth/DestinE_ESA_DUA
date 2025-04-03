package com.exprivia.dfda.duatiascheduler.business;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.exprivia.dfda.duatiascheduler.service.config.SchedulerConfig;
import com.exprivia.dfda.duatiascheduler.service.config.DocumentRepositoriesConfig.DocumentRepositoriesConfigHandler;
import com.exprivia.dfda.tia.model.DocumentRepository;
import com.exprivia.dfda.tia.service.RepositoryQueueService;
import com.exprivia.dfda.tia.service.exception.RepositoryQueueException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.sql.Timestamp;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class Scheduler {
	@Autowired
	private RepositoryQueueService repositoryService;

	@Autowired
	private SchedulerConfig config; 

	@Autowired
	private DocumentRepositoriesConfigHandler repoListHandler;

	private Map<String, Instant> repositoriedLastScheduled = new HashMap<>();

	public void doWork() {
		Integer pollingPeriod = config.getPollingPeriod();

		log.info("starting repo scheduling");

		while (true) {
			try {
				processRepositories();
			} catch (IOException e) {
				log.error("error processing repositories", e);
			}

			log.info("sleeping {} seconds", pollingPeriod);
			try {
				Thread.sleep(pollingPeriod * 1000);
			} catch (InterruptedException e) {
				log.error("pause interrupted", e);
				break;
			}
		}

	}

	private void processRepositories() throws IOException {
		Instant now = Instant.now();
		Timestamp tsNow = Timestamp.from(now);
		List<DocumentRepository> repositoryList = repoListHandler.getRepositoriesConfiguration();

		for (DocumentRepository docRepo : repositoryList) {

			if (docRepo.getEnabled() == true) {
				Instant lastScheduled = repositoriedLastScheduled.get(docRepo.getId());
				boolean wasScheduled = lastScheduled != null;
				Timestamp nextSchedule = Timestamp.from(
					(lastScheduled != null ? lastScheduled : now)
						.plusSeconds(docRepo.getFrequency()));

				log.info("evaluating repo {} with lastScheduled = {}, nextSchedule = {}, now = {}", 
					docRepo.getName(), 
					lastScheduled,
					nextSchedule.toInstant(), 
					tsNow.toInstant());

				if (wasScheduled == false ||
					tsNow.after(nextSchedule)) {

					log.info("processing repo {}", docRepo.getName());
					try {
						repositoryService.pushRepositoryEvent(docRepo);

						// TODO: evaluate if it is required to persist the last succesfull visit of the repository
						repositoriedLastScheduled.put(docRepo.getId(), now);
					} catch (RepositoryQueueException e) {
						log.error("cannot add repository visit to queue", e);
					}
				} else {
					log.info("repo {} skipped", docRepo.getName());
				}
			} else {
				log.info("repository \"{}\" is disabled by configuration", docRepo.getName());
			}
		}
	}

}
