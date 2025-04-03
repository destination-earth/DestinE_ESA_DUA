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
import com.exprivia.dfda.duatiaconfigurationmanager.service.services.KeywordDictionaryService;
import com.exprivia.dfda.tia.model.KeywordDictionary;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/tia/api/v1")
public class KeywordDictionaryController {
    private static final String SERVICE_ROOT = "/tia/api/v1";
    private static final String SERVICE_PATH = "/keyword-dictionaries";
    private static final String SERVICE_FULL_PATH = SERVICE_ROOT + SERVICE_PATH;

    @Autowired
    private KeywordDictionaryService kwdService;

	@GetMapping(SERVICE_PATH)
	public List<KeywordDictionary> getKeywordDictionaryList() {
		return kwdService.getKeywordDictionaryList();
	}

    @GetMapping(SERVICE_PATH + "/{id}")
	public ResponseEntity<KeywordDictionary> getKeywordDictionary(@PathVariable String id) {
		KeywordDictionary kwd = kwdService.getKeywordDictionary(id);
		return ResponseEntity.of(Optional.ofNullable(kwd));
	}

	@DeleteMapping(SERVICE_PATH + "/{id}")
	public ResponseEntity<Object> deleteKeywordDictionary(@PathVariable String id) {
		try {
			kwdService.deleteKeywordDictionary(id);
			log.info("removed keyword dictionary {}", id);
			return ResponseEntity.ok(id);
		} catch (NoSuchElementException e) {
			return ResponseEntity.notFound().build();
		} catch (ItemInUseException e) {
			log.error("cannot remove keyword dictionary {}", id, e);
			return ResponseEntity.unprocessableEntity().body(new ResponseErrorMessage(e));
		}	
	}

    @PostMapping(SERVICE_PATH)
	public ResponseEntity<KeywordDictionary> saveKeywordDictionary(@RequestBody KeywordDictionary keywordDictionary) {
		String id = kwdService.saveKeywordDictionary(keywordDictionary);
		log.info("saved keyword dictionary {}", id);
		return ResponseEntity
				.created(URI.create(SERVICE_FULL_PATH + id))
				.body(keywordDictionary);
	}
}
