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

import com.exprivia.dfda.duatiaconfigurationmanager.service.exceptions.InvalidRepositorySearchBindingException;
import com.exprivia.dfda.duatiaconfigurationmanager.service.services.RepositorySearchBindingService;
import com.exprivia.dfda.tia.model.RepositorySearchBinding;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/tia/api/v1")
public class RepositorySearchBindingController {
	private static final String SERVICE_ROOT = "/tia/api/v1";
	private static final String SERVICE_PATH = "/repository-search-bindings";
	private static final String SERVICE_FULL_PATH = SERVICE_ROOT + SERVICE_PATH;

	@Autowired
	private RepositorySearchBindingService repositorySearchBindingService;

	@GetMapping(SERVICE_PATH)
	public List<RepositorySearchBinding> getRepositorySearchBindings() {
		return repositorySearchBindingService.getRepositorySearchBindings();
	}

	@GetMapping(SERVICE_PATH + "/{id}")
	public ResponseEntity<RepositorySearchBinding> getRepositorySearchBinding(@PathVariable String id) {
		RepositorySearchBinding binding = repositorySearchBindingService.getRepositorySearchBinding(id);
		return ResponseEntity.of(Optional.ofNullable(binding));
	}

	@GetMapping(SERVICE_PATH + "/{id}/acronym")
	public ResponseEntity<String> getRepositorySearchBindingAcronym(@PathVariable String id) {
		try {
			return ResponseEntity.ok(
				repositorySearchBindingService.getRepositorySearchBindingAcronym(id));
		} catch (InvalidRepositorySearchBindingException e) {
			return ResponseEntity.notFound().build();
		}
	}

	@GetMapping(SERVICE_PATH + "/by-doc-repo/{docRepoId}")
	public List<RepositorySearchBinding> getRepositorySearchBindingByDocRepo(@PathVariable String docRepoId) {
		return repositorySearchBindingService.getRepositorySearchBindingsByDocumentRepository(docRepoId);
	}

	@DeleteMapping(SERVICE_PATH + "/{id}")
	public ResponseEntity<String> deleteRepositorySearchBinding(@PathVariable String id) {
		try {
			repositorySearchBindingService.deleteRepositorySearchBinding(id);
			log.info("removed repository search binding {}", id);
			return ResponseEntity.ok(id);
		} catch (NoSuchElementException e) {
			log.error("cannot remove repository search binding {}", id, e);
			return ResponseEntity.notFound().build();
		}
	}

	@PostMapping(SERVICE_PATH)
	public ResponseEntity<RepositorySearchBinding> saveRepositorySearchBinding(@RequestBody RepositorySearchBinding binding) {
		try {
			String id = repositorySearchBindingService.saveRepositorySearchBinding(binding);
			log.info("saved repository search binding {}", id);
			return ResponseEntity
				.created(URI.create(SERVICE_FULL_PATH + id))
				.body(binding);		
		} catch (InvalidRepositorySearchBindingException e) {
			log.error("cannot save repository search binding", e);
			return ResponseEntity.unprocessableEntity().build();
		}
	}
}
