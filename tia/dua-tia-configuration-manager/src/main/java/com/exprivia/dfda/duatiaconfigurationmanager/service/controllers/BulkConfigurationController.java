package com.exprivia.dfda.duatiaconfigurationmanager.service.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.exprivia.dfda.duatiaconfigurationmanager.service.exceptions.InvalidRepositorySearchBindingException;
import com.exprivia.dfda.duatiaconfigurationmanager.service.models.BulkConfiguration;
import com.exprivia.dfda.duatiaconfigurationmanager.service.models.ResponseErrorMessage;
import com.exprivia.dfda.duatiaconfigurationmanager.service.services.BulkConfigurationService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@RestController
@RequestMapping("/tia/api/v1")
public class BulkConfigurationController {
    private static final String SERVICE_PATH = "/bulk-configuration";

    @Autowired
    private BulkConfigurationService bulkConfigurationService;

    @GetMapping(SERVICE_PATH)
    public BulkConfiguration getMethodName() {
        return bulkConfigurationService.readConfiguration();
    }

    @PostMapping(SERVICE_PATH)
    public ResponseEntity<Object> loadConfiguration(@RequestBody BulkConfiguration bulkConfiguration) {
        
        try {
            bulkConfigurationService.saveConfiguration(bulkConfiguration);
            return ResponseEntity.ok(null);
        } catch (InvalidRepositorySearchBindingException e) {
            log.error("cannot load configuration", e);
            return ResponseEntity.unprocessableEntity().body(new ResponseErrorMessage(e));
        }
    }

    @DeleteMapping(SERVICE_PATH)
	public void dropConfiguration() {
        bulkConfigurationService.dropConfiguration();

        log.info("configuration dropped");
    }
    
}
