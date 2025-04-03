package com.exprivia.dfda.duatiaconfigurationmanager.service.controllers;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.exprivia.dfda.duatiaconfigurationmanager.service.services.QueryMaxPublicationDateService;
import com.exprivia.dfda.tia.model.QueryMaxPublicationDate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/tia/api/v1")
public class QueryMaxPublicationDateController {
    private static final String SERVICE_ROOT = "/tia/api/v1";
    private static final String SERVICE_PATH = "/query-max-publication-date";
    private static final String SERVICE_FULL_PATH = SERVICE_ROOT + SERVICE_PATH;

    @Autowired
    private QueryMaxPublicationDateService service;

	@GetMapping(SERVICE_PATH)
	public List<QueryMaxPublicationDate> getQueryMaxPublicationDateList() {
		return service.getQueryMaxPublicationDateList();
	}


    @GetMapping(SERVICE_PATH + "/{id}")
	public ResponseEntity<QueryMaxPublicationDate> getQueryMaxPublicationDate(
		@PathVariable String id) {
		
		QueryMaxPublicationDate qmpd = service.getMaxQueryPublicationDate(id);
		return ResponseEntity.of(Optional.ofNullable(qmpd));
	}

    @GetMapping(SERVICE_PATH + "/{searchBindingId}/{repositoryId}/{mission}")
	public ResponseEntity<QueryMaxPublicationDate> getQueryMaxPublicationDate(
		@PathVariable String searchBindingId, 
		@PathVariable String repositoryId,
		@PathVariable String mission) {
		
		QueryMaxPublicationDate qmpd = service.getMaxQueryPublicationDate(
			QueryMaxPublicationDate.generateId(searchBindingId, repositoryId, mission)
		);
		return ResponseEntity.of(Optional.ofNullable(qmpd));
	}

	@DeleteMapping(SERVICE_PATH + "/{searchBindingId}/{repositoryId}/{mission}")
	public ResponseEntity<Object> deleteQueryMaxPublicationDate(
		@PathVariable String searchBindingId, 
		@PathVariable String repositoryId,
		@PathVariable String mission) {

		String id = QueryMaxPublicationDate.generateId(searchBindingId, repositoryId, mission);
		try {
			service.deleteMaxQueryPublicationDate(id);
			log.info("removed query max publication date {}", id);
			return ResponseEntity.ok(id);
		} catch (NoSuchElementException e) {
			return ResponseEntity.notFound().build();
		}	
	}

    @PostMapping(SERVICE_PATH)
	public ResponseEntity<QueryMaxPublicationDate> saveQueryMaxPublicationDate(@RequestBody QueryMaxPublicationDate queryMaxPublicationDate) {
		String id = service.saveMaxQueryPublicationDate(queryMaxPublicationDate);
		log.info("saved query max publication date {}", id);
		return ResponseEntity
				.created(URI.create(
					SERVICE_FULL_PATH + "/" + 
					id.replace(" ", "%20")))
				.body(queryMaxPublicationDate);
	}
}
