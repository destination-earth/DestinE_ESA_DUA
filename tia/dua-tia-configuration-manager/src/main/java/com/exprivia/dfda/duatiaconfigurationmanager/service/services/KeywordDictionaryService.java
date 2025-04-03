package com.exprivia.dfda.duatiaconfigurationmanager.service.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.exprivia.dfda.duatiaconfigurationmanager.service.exceptions.ItemInUseException;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.KeywordDictionaryRepository;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.RepositorySearchBindingRepository;
import com.exprivia.dfda.tia.model.KeywordDictionary;
import com.exprivia.dfda.tia.model.RepositorySearchBinding;

@Service
public class KeywordDictionaryService {

    @Autowired
    private KeywordDictionaryRepository kwRepo;

    @Autowired
    private RepositorySearchBindingRepository bindingRepository;

    public KeywordDictionary getKeywordDictionary(String id) {
        return kwRepo.findById(id).orElse(null);
    }

    public void deleteKeywordDictionary(String id) throws ItemInUseException {
        KeywordDictionary item = kwRepo.findById(id).orElseThrow();

        for (RepositorySearchBinding binding : bindingRepository.findAll()) {
            if (binding.getKeywordDictionaryId().equals(id)) {
                throw new ItemInUseException("KeywordDictionary " + id + " is in use by RepositorySearchBinding " + binding.getId());
            }
        }

        kwRepo.delete(item);
    }

    public String saveKeywordDictionary(KeywordDictionary keywordDictionary) {
        kwRepo.save(keywordDictionary);
		return keywordDictionary.getId();
    }

    public List<KeywordDictionary> getKeywordDictionaryList() {
        return kwRepo.findAll();
    }
}
