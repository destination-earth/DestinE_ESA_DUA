package com.exprivia.dfda.duatiaconfigurationmanager.service.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.exprivia.dfda.duatiaconfigurationmanager.service.exceptions.InvalidRepositorySearchBindingException;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.DocumentRepositoryRepository;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.DocumentTypeRepository;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.InitiativeRepository;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.KeywordDictionaryRepository;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.RepositorySearchBindingRepository;
import com.exprivia.dfda.tia.model.DocumentType;
import com.exprivia.dfda.tia.model.Initiative;
import com.exprivia.dfda.tia.model.KeywordDictionary;
import com.exprivia.dfda.tia.model.RepositorySearchBinding;

@Service
public class RepositorySearchBindingService {
    @Autowired
    private RepositorySearchBindingRepository repository;

    @Autowired
    private InitiativeRepository initiativeRepository;

    @Autowired
    private DocumentTypeRepository documentTypeRepository;

    @Autowired
    private KeywordDictionaryRepository keywordDictionaryRepository;

    @Autowired
    private DocumentRepositoryRepository documentRepositoryRepository;

    public List<RepositorySearchBinding> getRepositorySearchBindings() {
        return repository.findAll();
    }

    public List<RepositorySearchBinding> getRepositorySearchBindingsByDocumentRepository(String documentRepositoryId) {
        List<RepositorySearchBinding> retList = new ArrayList<>();
        for (RepositorySearchBinding binding : repository.findAll()) {
            if (binding.getDocumentRepositoryIds().contains(documentRepositoryId)) {
                retList.add(binding);
            }
        }
        return retList;
    }

    public String saveRepositorySearchBinding(RepositorySearchBinding binding) throws InvalidRepositorySearchBindingException {
        checkRepositorySearchBinding(binding);

        repository.save(binding);
        return binding.getId();
    }

    public void deleteRepositorySearchBinding(String id) {
        RepositorySearchBinding item = repository.findById(id).orElseThrow();

        repository.delete(item);
    }

    public RepositorySearchBinding getRepositorySearchBinding(String id) {
        return repository.findById(id).orElse(null);
    }

    public String getRepositorySearchBindingAcronym(String id) throws InvalidRepositorySearchBindingException {
        RepositorySearchBinding binding = getRepositorySearchBinding(id);
        if (binding == null) {
            throw new InvalidRepositorySearchBindingException("repository search binding " + id + " does not exist");
        }
        checkRepositorySearchBinding(binding);

        Initiative initiative = initiativeRepository.findById(binding.getInitiativeId()).orElseThrow();
        DocumentType documentType = documentTypeRepository.findById(binding.getDocumentTypeId()).orElseThrow();
        KeywordDictionary keywordDictionary = keywordDictionaryRepository.findById(binding.getKeywordDictionaryId()).orElseThrow();
        
        StringBuilder sb = new StringBuilder();
        sb.append(initiative.getShortName());
        sb.append("-");
        sb.append(documentType.getShortName());
        sb.append("-");
        sb.append(keywordDictionary.getShortName());
        return sb.toString();
    }

    private void checkRepositorySearchBinding(RepositorySearchBinding binding) throws InvalidRepositorySearchBindingException {
        if (initiativeRepository.findById(binding.getInitiativeId()).isEmpty()) {
            throw new InvalidRepositorySearchBindingException("Initiative not found: " + binding.getInitiativeId());
        }
        if (documentTypeRepository.findById(binding.getDocumentTypeId()).isEmpty()) {
            throw new InvalidRepositorySearchBindingException("DocumentType not found: " + binding.getDocumentTypeId());
        }
        if (keywordDictionaryRepository.findById(binding.getKeywordDictionaryId()).isEmpty()) {
            throw new InvalidRepositorySearchBindingException("KeywordDictionary not found: " + binding.getKeywordDictionaryId());
        }

        for (String documentRepositoryId : binding.getDocumentRepositoryIds()) {
            if (documentRepositoryRepository.findById(documentRepositoryId).isEmpty()) {
                throw new InvalidRepositorySearchBindingException("DocumentRepository not found: " + documentRepositoryId);
            }
        }
    }
}
