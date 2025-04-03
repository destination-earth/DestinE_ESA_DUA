package com.exprivia.dfda.duatiascheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import com.exprivia.dfda.duatiascheduler.business.Scheduler;
import com.exprivia.dfda.tia.service.RepositoryQueueService;

import java.io.IOException;


@SpringBootApplication
@ComponentScan(basePackageClasses = { DuaTiaSchedulerApplication.class, RepositoryQueueService.class})
public class DuaTiaSchedulerApplication implements CommandLineRunner {
	@Autowired
	private Scheduler scheduler;

	public static void main(String[] args) {
		SpringApplication.run(DuaTiaSchedulerApplication.class, args);
	}

	@Override
	public void run(String ... args) throws IOException {
		scheduler.doWork();
	}
}
