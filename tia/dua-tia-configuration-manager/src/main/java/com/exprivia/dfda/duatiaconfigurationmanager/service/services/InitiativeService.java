package com.exprivia.dfda.duatiaconfigurationmanager.service.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.exprivia.dfda.duatiaconfigurationmanager.service.exceptions.ItemInUseException;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.InitiativeRepository;
import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.RepositorySearchBindingRepository;
import com.exprivia.dfda.tia.model.Initiative;
import com.exprivia.dfda.tia.model.RepositorySearchBinding;

@Service
public class InitiativeService {
    @Autowired
    private InitiativeRepository initiativeRepository;

    @Autowired
    private RepositorySearchBindingRepository bindingRepository;

    public Initiative getInitiative(String id) {
        return initiativeRepository.findById(id).orElse(null);
    }

    public void deleteInitiative(String id) throws ItemInUseException {
        Initiative item = initiativeRepository.findById(id).orElseThrow();
        
        for (RepositorySearchBinding binding : bindingRepository.findAll()) {
            if (binding.getInitiativeId().equals(id)) {
                throw new ItemInUseException("Initiative " + id + " is in use by RepositorySearchBinding " + binding.getId());
            }
        }

        initiativeRepository.delete(item);
    }

    public String saveInitiative(Initiative initiative) {
        initiativeRepository.save(initiative);
        return initiative.getId();
    }

    public List<Initiative> getInitiatives() {
        return initiativeRepository.findAll();
    }

}
