package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.BadRepositoryConfigurationException;
import com.exprivia.dfda.tia.model.DocumentRepository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
public class DocumentRepositoryAccess {
    @NonNull
    private URL url;

    private String username;
    private String password;
    private String credentials;
    private Integer pageLimit;
    private Integer pauseBetweenPages;

    public static DocumentRepositoryAccess createFromDocumentRepositoryDefinition(DocumentRepository docRepo) throws BadRepositoryConfigurationException {
        URL url = null;
		try {
			url = new URI(docRepo.getUrl()).toURL();
		} catch (MalformedURLException | URISyntaxException e) {
			throw new BadRepositoryConfigurationException("repository \"" + docRepo.getName() + "\" bad url: " + docRepo.getUrl(), e);
		}

        if (docRepo.getCredentials() != null) {
            if (docRepo.getCredentials().contains(":")) {
                String [] credentials = docRepo.getCredentials().split(":");

                return new DocumentRepositoryAccess(
                    url, 
                    credentials[0],
                    credentials[1],
                    null,
                    docRepo.getPageLimit(),
                    docRepo.getPauseBetweenPages()
                    );
            } else {
                return new DocumentRepositoryAccess(
                    url, 
                    null, 
                    null, 
                    docRepo.getCredentials(),
                    docRepo.getPageLimit(),
                    docRepo.getPauseBetweenPages());
            }
        } else {
            return new DocumentRepositoryAccess(url, null, null, null, docRepo.getPageLimit(), docRepo.getPauseBetweenPages());
        }
    }
}
