package com.onesley.oneclick.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Microservice search — Phase 2 §21 spec senior dev (4e service extrait).
 *
 * <p>Port 8087. Recherche full-text restaurants via Elasticsearch 7.17 (Phase 3.4)
 * avec fallback tsvector PostgreSQL si ES :9200 down.
 *
 * <p>{@link org.springframework.scheduling.annotation.EnableScheduling} active le
 * job {@code RestaurantEsSyncService.deltaSync()} (toutes les 60s).
 */
@SpringBootApplication
@EnableScheduling
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
