package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.driver.DocumentRepositoryDriverInterface;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.driver.implementation.DocumentRepositoryDriverJrc;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.driver.implementation.DocumentRepositoryDriverOpenAlex;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.driver.implementation.DocumentRepositoryDriverScopus;

@Component
public class DocumentRepositoryDriverFactory {
    @Autowired
    private DocumentRepositoryDriverJrc jrc;

    @Autowired
    private DocumentRepositoryDriverScopus scopus;

    @Autowired
    private DocumentRepositoryDriverOpenAlex openAlex;

    public DocumentRepositoryDriverInterface factory(String driverName) {
        if (driverName.equals(jrc.getDriverName())) return jrc;
        if (driverName.equals(scopus.getDriverName())) return scopus;
        if (driverName.equals(openAlex.getDriverName())) return openAlex;
        
        throw new IllegalArgumentException("unrecognized document repository driver: " + driverName);
    }
}
