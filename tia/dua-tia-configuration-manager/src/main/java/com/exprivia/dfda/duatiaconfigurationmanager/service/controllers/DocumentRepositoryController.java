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

import com.exprivia.dfda.duatiaconfigurationmanager.service.exceptions.ItemInUseException;
import com.exprivia.dfda.duatiaconfigurationmanager.service.models.ResponseErrorMessage;
import com.exprivia.dfda.duatiaconfigurationmanager.service.services.DocumentRepositoryService;
import com.exprivia.dfda.tia.model.DocumentRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/tia/api/v1")
public class DocumentRepositoryController {
    private static final String SERVICE_ROOT = "/tia/api/v1";
    private static final String SERVICE_PATH = "/document-repositories";
    private static final String SERVICE_FULL_PATH = SERVICE_ROOT + SERVICE_PATH;

    @Autowired
    private DocumentRepositoryService docRepoService;

	@GetMapping(SERVICE_PATH)
	public List<DocumentRepository> getDocumentRepositoryList() {
		return docRepoService.getDocumentRepositoryList();
	}

    @GetMapping(SERVICE_PATH + "/{id}")
	public ResponseEntity<DocumentRepository> getDocumentRepository(@PathVariable String id) {
		DocumentRepository docRepo = docRepoService.getDocumentRepository(id);
		return ResponseEntity.of(Optional.ofNullable(docRepo));
	}

	@DeleteMapping(SERVICE_PATH + "/{id}")
	public ResponseEntity<Object> deleteDocumentRepository(@PathVariable String id) {
		try {
			docRepoService.deleteDocumentRepository(id);
			log.info("deleted document repository {}", id);
			return ResponseEntity.ok(id);
		} catch (NoSuchElementException e) {
			return ResponseEntity.notFound().build();
		} catch (ItemInUseException e) {
			log.error("cannot remove document repository {}", id, e);
			return ResponseEntity.unprocessableEntity().body(new ResponseErrorMessage(e));
		}		
	}

    @PostMapping(SERVICE_PATH)
	public ResponseEntity<DocumentRepository> saveDocumentRepository(@RequestBody DocumentRepository documentRepository) {
		String id = docRepoService.saveDocumentRepository(documentRepository);
		log.info("saved document repository {}", id);
		return ResponseEntity
				.created(URI.create(SERVICE_FULL_PATH + id))
				.body(documentRepository);
	}
}
