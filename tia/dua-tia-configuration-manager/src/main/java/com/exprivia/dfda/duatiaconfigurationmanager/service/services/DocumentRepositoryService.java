package com.exprivia.dfda.duatiaconfigurationmanager.service.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.exprivia.dfda.duatiaconfigurationmanager.service.exceptions.ItemInUseException;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.DocumentRepositoryRepository;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.RepositorySearchBindingRepository;
import com.exprivia.dfda.tia.model.DocumentRepository;
import com.exprivia.dfda.tia.model.RepositorySearchBinding;

@Service
public class DocumentRepositoryService {
    @Autowired
    private DocumentRepositoryRepository documentRepositoryRepository;

    @Autowired
    private RepositorySearchBindingRepository bindingRepository;

    public DocumentRepository getDocumentRepository(String id) {
        return documentRepositoryRepository.findById(id).orElse(null);
    }

    public void deleteDocumentRepository(String id) throws ItemInUseException {
        DocumentRepository item = documentRepositoryRepository.findById(id).orElseThrow();

        for (RepositorySearchBinding binding : bindingRepository.findAll()) {
            for (String docRepoId : binding.getDocumentRepositoryIds()) {
                if (docRepoId.equals(id)) {
                    throw new ItemInUseException("Document repository " + id + " is in use by RepositorySearchBinding " + binding.getId());
                }
            }
        }

        documentRepositoryRepository.delete(item);
    }

    public String saveDocumentRepository(DocumentRepository documentRepository) {
        documentRepositoryRepository.save(documentRepository);
		return documentRepository.getId();
    }

    public List<DocumentRepository> getDocumentRepositoryList() {
        return documentRepositoryRepository.findAll();
    }
}
