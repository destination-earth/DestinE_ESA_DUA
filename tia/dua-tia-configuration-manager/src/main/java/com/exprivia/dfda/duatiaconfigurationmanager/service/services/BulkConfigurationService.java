package com.exprivia.dfda.duatiaconfigurationmanager.service.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.exprivia.dfda.duatiaconfigurationmanager.service.exceptions.InvalidRepositorySearchBindingException;
import com.exprivia.dfda.duatiaconfigurationmanager.service.models.BulkConfiguration;
import com.exprivia.dfda.duatiaconfigurationmanager.service.models.RepositorySearchBindingReference;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.DocumentRepositoryRepository;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.DocumentTypeRepository;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.InitiativeRepository;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.KeywordDictionaryRepository;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.RepositorySearchBindingRepository;
import com.exprivia.dfda.tia.model.DocumentRepository;
import com.exprivia.dfda.tia.model.DocumentType;
import com.exprivia.dfda.tia.model.Initiative;
import com.exprivia.dfda.tia.model.KeywordDictionary;
import com.exprivia.dfda.tia.model.RepositorySearchBinding;

@Service
public class BulkConfigurationService {
    @Autowired
    private RepositorySearchBindingRepository bindingRepository;

    @Autowired
    private InitiativeRepository initiativeRepository;

    @Autowired
    private DocumentTypeRepository documentTypeRepository;

    @Autowired
    private KeywordDictionaryRepository keywordDictionaryRepository;

    @Autowired
    private DocumentRepositoryRepository documentRepositoryRepository;


    @Autowired
    private RepositorySearchBindingService bindingService;

    @Autowired
    private InitiativeService initiativeService;

    @Autowired
    private DocumentTypeService documentTypeService;

    @Autowired
    private KeywordDictionaryService keywordDictionaryService;

    @Autowired
    private DocumentRepositoryService documentRepositoryService;

    public BulkConfiguration readConfiguration() {
        return new BulkConfiguration(
            initiativeService.getInitiatives(), 
            documentTypeService.getDocumentTypes(), 
            documentRepositoryService.getDocumentRepositoryList(), 
            keywordDictionaryService.getKeywordDictionaryList(), 
            new ArrayList<>(),
            bindingService.getRepositorySearchBindings()
            );
    }

    public void saveConfiguration(BulkConfiguration bulkConfiguration) throws InvalidRepositorySearchBindingException {
        dropConfiguration();

        List<String> initiativeIds = new ArrayList<>();
        for (Initiative initiative : bulkConfiguration.getInitiatives()) {
            initiativeIds.add(initiativeService.saveInitiative(initiative));
        }

        List<String> documentTypeIds = new ArrayList<>();
        for (DocumentType documentType : bulkConfiguration.getDocumentTypes()) {
            documentTypeIds.add(documentTypeService.saveDocumentType(documentType));
        }

        List<String> documentRepositoryIds = new ArrayList<>();
        for (DocumentRepository documentRepository : bulkConfiguration.getDocumentRepositories()) {
            documentRepositoryIds.add(documentRepositoryService.saveDocumentRepository(documentRepository));
        }

        List<String> keywordDictionaryIds = new ArrayList<>();
        for (KeywordDictionary keywordDictionary : bulkConfiguration.getKeywordDictionaries()) {
            keywordDictionaryIds.add(keywordDictionaryService.saveKeywordDictionary(keywordDictionary));
        }

        if (bulkConfiguration.getRepositorySearchBindingReferences() != null &&
            bulkConfiguration.getRepositorySearchBindingReferences().size() > 0) {
            for (RepositorySearchBindingReference repositorySearchBindingReference : bulkConfiguration.getRepositorySearchBindingReferences()) {
                String initiativeId = initiativeIds.get(repositorySearchBindingReference.getInitiativeIdx());
                String documentTypeId = documentTypeIds.get(repositorySearchBindingReference.getDocumentTypeIdx());
                String keywordDictionaryId = keywordDictionaryIds.get(repositorySearchBindingReference.getKeywordDictionaryIdx());

                List<String> bindingDocumentRepositoryIds = new ArrayList<>();
                for (Integer documentRepositoryIdx : repositorySearchBindingReference.getDocumentRepositoryIdxs()) {
                    bindingDocumentRepositoryIds.add(documentRepositoryIds.get(documentRepositoryIdx));

                }

                RepositorySearchBinding binding = new RepositorySearchBinding(
                    initiativeId, 
                    documentTypeId, 
                    keywordDictionaryId, 
                    bindingDocumentRepositoryIds);

                bindingService.saveRepositorySearchBinding(binding);
            }
        }
        
        if (bulkConfiguration.getRepositorySearchBindings() != null &&
            bulkConfiguration.getRepositorySearchBindings().size() > 0) {
            for (RepositorySearchBinding repositorySearchBinding : bulkConfiguration.getRepositorySearchBindings()) {
                bindingService.saveRepositorySearchBinding(repositorySearchBinding);
            }
        }
    }

    public void dropConfiguration() {
        bindingRepository.deleteAll();
        initiativeRepository.deleteAll();
        documentTypeRepository.deleteAll();
        keywordDictionaryRepository.deleteAll();
        documentRepositoryRepository.deleteAll();
    }

}
