package com.exprivia.dfda.duatiaconfigurationmanager.service.services;


import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.exprivia.dfda.duatiaconfigurationmanager.service.repositories.CredentialRepository;
import com.exprivia.dfda.tia.model.Credential;

@Service
public class CredentialService {
    @Autowired
    private CredentialRepository credentialRepository;

    public Credential getCredential(String id) {
        return credentialRepository.findById(id).orElse(null);
    }

    public void deleteCredential(String id) {
        Credential item = credentialRepository.findById(id).orElseThrow();

        credentialRepository.delete(item);
    }

    public String saveCredential(Credential credential) {
        credentialRepository.save(credential);
		return credential.getId();
    }

    public List<Credential> getCredentialList() {
        return credentialRepository.findAll();
    }
}
