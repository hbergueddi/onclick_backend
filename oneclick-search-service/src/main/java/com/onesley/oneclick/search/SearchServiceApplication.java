package com.onesley.oneclick.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservice search — Phase 2 §21 spec senior dev (4e service extrait).
 *
 * <p>Port 8087. Recherche full-text restaurants via tsvector PostgreSQL.
 * En Phase 3+, l'index sera externalisé vers Elasticsearch (cf §20 spec).
 */
@SpringBootApplication
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
