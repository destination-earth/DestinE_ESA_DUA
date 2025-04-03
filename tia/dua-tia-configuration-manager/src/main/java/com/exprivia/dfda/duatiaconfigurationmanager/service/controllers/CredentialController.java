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

import com.exprivia.dfda.duatiaconfigurationmanager.service.services.CredentialService;
import com.exprivia.dfda.tia.model.Credential;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/tia/api/v1")
public class CredentialController {
    private static final String SERVICE_ROOT = "/tia/api/v1";
    private static final String SERVICE_PATH = "/credentials";
    private static final String SERVICE_FULL_PATH = SERVICE_ROOT + SERVICE_PATH;

    @Autowired
    private CredentialService credentialService;

	@GetMapping(SERVICE_PATH)
	public List<Credential> getCredentialList() {
		return credentialService.getCredentialList();
	}

    @GetMapping(SERVICE_PATH + "/{id}")
	public ResponseEntity<Credential> getDocumentRepository(@PathVariable String id) {
		Credential credential = credentialService.getCredential(id);
		return ResponseEntity.of(Optional.ofNullable(credential));
	}

	@DeleteMapping(SERVICE_PATH + "/{id}")
	public ResponseEntity<String> deleteCredential(@PathVariable String id) {
		try {
			credentialService.deleteCredential(id);
			log.info("deleted credential {}", id);
			return ResponseEntity.ok(id);
		} catch (NoSuchElementException e) {
			log.error("cannot delete credential {}", id, e);
			return ResponseEntity.notFound().build();
		}
	}

    @PostMapping(SERVICE_PATH)
	public ResponseEntity<Credential> saveCredential(@RequestBody Credential credential) {
		String id = credentialService.saveCredential(credential);
		log.info("saved credential {}", id);
		return ResponseEntity
				.created(URI.create(SERVICE_FULL_PATH + id))
				.body(credential);
	}
}
