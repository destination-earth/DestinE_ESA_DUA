package com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.driver;

import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.DocumentRepositoryAccess;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.CannotContactRepositoryException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.exceptions.CannotDecodeRepositoryResultsException;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.query.DocumentRepositoryQuery;
import com.exprivia.dfda.duatiarepositoryharvester.business.docrepo.result.DocumentRepositoryQueryResult;

public interface DocumentRepositoryDriverInterface {

    public String getDriverName();

    /**
     * reset the driver to begin a new paging query
     */
    default void initializeDriver() {
    }

    public DocumentRepositoryQueryResult query(
        String groupName,
        DocumentRepositoryAccess repoAccess, 
        DocumentRepositoryQuery predicate) 
        throws CannotContactRepositoryException, CannotDecodeRepositoryResultsException;
    
    /**
     * indicates if a custom pagination is implemented into the driver
     * @return
     */
    default boolean customPaginationImplemention() {
        return false;
    }

    /**
     * additional evaluation to determine if the pagination should stop
     * @return boolean
     */
    default boolean proceedWithQueryPage() {
        return false;
    }

    String getQueryAsString(DocumentRepositoryQuery query, int pageLimit);
}
