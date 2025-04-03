package com.exprivia.dfda.tia.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.exprivia.dfda.tia.model.DocumentStatus;

/*
 * REMEMBER to put these annotations on the main application class:
 * 
 * @ComponentScan({<main-app-package>, "com.exprivia.dfda.tia"})
 * @EnableRedisRepositories({<main-app-package>, "com.exprivia.dfda.tia"})
 */

@Repository
public interface DocumentStatusRepository extends CrudRepository<DocumentStatus, String> {
    
}
