package com.exprivia.dfda.duatiaconfigurationmanager.service.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.exprivia.dfda.duatiaconfigurationmanager.service.exceptions.ItemInUseException;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.DocumentTypeRepository;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.RepositorySearchBindingRepository;
import com.exprivia.dfda.tia.model.DocumentType;
import com.exprivia.dfda.tia.model.RepositorySearchBinding;


@Service
public class DocumentTypeService {
    @Autowired
    private DocumentTypeRepository documentTypeRepository;

    @Autowired
    private RepositorySearchBindingRepository bindingRepository;

    public DocumentType getDocumentType(String id) {
        return documentTypeRepository.findById(id).orElse(null);
    }

    public void deleteDocumentType(String id) throws ItemInUseException {
        DocumentType item = documentTypeRepository.findById(id).orElseThrow();

        for (RepositorySearchBinding binding : bindingRepository.findAll()) {
            if (binding.getDocumentTypeId().equals(id)) {
                throw new ItemInUseException("DocumentType " + id + " is in use by RepositorySearchBinding " + binding.getId());
            }
        }

        documentTypeRepository.delete(item);
    }

    public String saveDocumentType(DocumentType documentType) {
        documentTypeRepository.save(documentType);
        return documentType.getId();
    }

    public List<DocumentType> getDocumentTypes() {
        return documentTypeRepository.findAll();
    }

}
