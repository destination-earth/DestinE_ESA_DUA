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
import com.exprivia.dfda.duatiaconfigurationmanager.service.services.InitiativeService;
import com.exprivia.dfda.tia.model.Initiative;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/tia/api/v1")
public class InitiativeController {
    private static final String SERVICE_ROOT = "/tia/api/v1";
    private static final String SERVICE_PATH = "/initiatives";
    private static final String SERVICE_FULL_PATH = SERVICE_ROOT + SERVICE_PATH;

    @Autowired
    private InitiativeService initiativeService;

	@GetMapping(SERVICE_PATH)
	public List<Initiative> getInitiatives() {
		return initiativeService.getInitiatives();
	}

    @GetMapping(SERVICE_PATH + "/{id}")
	public ResponseEntity<Initiative> getInitiative(@PathVariable String id) {
		Initiative initiative = initiativeService.getInitiative(id);
		return ResponseEntity.of(Optional.ofNullable(initiative));
	}

	@DeleteMapping(SERVICE_PATH + "/{id}")
	public ResponseEntity<Object> deleteInitiative(@PathVariable String id) {
		try {
			initiativeService.deleteInitiative(id);
			log.info("removed initiative {}", id);
			return ResponseEntity.ok(id);
		} catch (NoSuchElementException e) {
			return ResponseEntity.notFound().build();
		} catch (ItemInUseException e) {
			log.error("cannot remove initiative {}", id, e);
			return ResponseEntity.unprocessableEntity().body(new ResponseErrorMessage(e));
		}
	}

    @PostMapping(SERVICE_PATH)
	public ResponseEntity<Initiative> saveInitiative(@RequestBody Initiative initiative) {
		String id = initiativeService.saveInitiative(initiative);
		log.info("saved initiative {}", id);
		return ResponseEntity
				.created(URI.create(SERVICE_FULL_PATH + id))
				.body(initiative);
	}
}
