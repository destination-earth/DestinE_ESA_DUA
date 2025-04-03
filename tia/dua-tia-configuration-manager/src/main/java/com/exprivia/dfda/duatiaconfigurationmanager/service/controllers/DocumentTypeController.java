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
import com.exprivia.dfda.duatiaconfigurationmanager.service.services.DocumentTypeService;
import com.exprivia.dfda.tia.model.DocumentType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/tia/api/v1")
public class DocumentTypeController {
    private static final String SERVICE_ROOT = "/tia/api/v1";
    private static final String SERVICE_PATH = "/document-types";
    private static final String SERVICE_FULL_PATH = SERVICE_ROOT + SERVICE_PATH;

    @Autowired
    private DocumentTypeService documentTypeService;

	@GetMapping(SERVICE_PATH)
	public List<DocumentType> getDocumentTypes() {
		return documentTypeService.getDocumentTypes();
	}

    @GetMapping(SERVICE_PATH + "/{id}")
	public ResponseEntity<DocumentType> getDocumentType(@PathVariable String id) {
		DocumentType documentType = documentTypeService.getDocumentType(id);
		return ResponseEntity.of(Optional.ofNullable(documentType));
	}

	@DeleteMapping(SERVICE_PATH + "/{id}")
	public ResponseEntity<Object> deleteDocumentType(@PathVariable String id) {
		try {
			documentTypeService.deleteDocumentType(id);
			log.info("removed document type {}", id);
			return ResponseEntity.ok(id);
		} catch (NoSuchElementException e) {
			return ResponseEntity.notFound().build();
		} catch (ItemInUseException e) {
			log.error("cannot remove document type {}", id, e);
			return ResponseEntity.unprocessableEntity().body(new ResponseErrorMessage(e));
		}
	}

    @PostMapping(SERVICE_PATH)
	public ResponseEntity<DocumentType> saveDocumentType(@RequestBody DocumentType documentType) {
		String id = documentTypeService.saveDocumentType(documentType);
		log.info("saved document type {}", id);
		return ResponseEntity
				.created(URI.create(SERVICE_FULL_PATH + id))
				.body(documentType);
	}
}
