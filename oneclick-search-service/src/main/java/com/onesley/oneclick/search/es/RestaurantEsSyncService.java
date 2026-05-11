package com.onesley.oneclick.search.es;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sync PostgreSQL → Elasticsearch — projection {@code restaurants} → index ES.
 *
 * <p>Phase 3.4 spec §X : Postgres reste la source de vérité. ES est une projection
 * dénormalisée (lecture full-text rapide). Pas d'écriture cross-direction.
 *
 * <p>Stratégie :
 * <ul>
 *   <li>Au démarrage : full bulk reindex (≤ 1042 restaurants en local)</li>
 *   <li>Toutes les 60s : delta sync sur {@code updated_at > last_sync_ts}</li>
 * </ul>
 *
 * <p>Quand on activera la Phase 3.3 events Kafka, on ajoutera un consumer sur
 * {@code restaurant.updated} pour un sync near-realtime (≤ 1s drift).
 *
 * <p>Resilience : si ES :9200 down, log warn + skip (pas d'exception cascade).
 * tsvector PostgreSQL reste opérationnel comme fallback (cf SearchController).
 */
@Service
public class RestaurantEsSyncService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantEsSyncService.class);

    private final JdbcTemplate jdbc;
    private final RestaurantEsRepository esRepo;
    private final boolean esEnabled;

    private volatile java.time.Instant lastSyncTs = java.time.Instant.EPOCH;

    public RestaurantEsSyncService(JdbcTemplate jdbc,
                                    RestaurantEsRepository esRepo,
                                    @Value("${app.search.elasticsearch.enabled:true}") boolean esEnabled) {
        this.jdbc = jdbc;
        this.esRepo = esRepo;
        this.esEnabled = esEnabled;
    }

    /** Bulk reindex initial au démarrage de l'app. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!esEnabled) {
            log.info("ES sync disabled (app.search.elasticsearch.enabled=false)");
            return;
        }
        try {
            log.info("ES startup bulk reindex — pulling restaurants from Postgres...");
            int indexed = bulkReindex();
            log.info("ES startup bulk reindex done : {} restaurants indexed", indexed);
        } catch (Exception e) {
            log.warn("ES startup sync failed : {} — fallback tsvector PostgreSQL", e.getMessage());
        }
    }

    /** Delta sync toutes les 60s : restaurants modifiés depuis lastSyncTs. */
    @Scheduled(fixedDelayString = "${app.search.sync-interval-ms:60000}", initialDelay = 60000)
    public void deltaSync() {
        if (!esEnabled) return;
        try {
            java.time.Instant now = java.time.Instant.now();
            List<RestaurantEsDoc> docs = jdbc.query(
                """
                SELECT id, tenant_id, name, city, address, description, phone, status, updated_at
                FROM restaurants
                WHERE deleted_at IS NULL
                  AND updated_at > ?
                ORDER BY updated_at
                LIMIT 500
                """,
                ps -> ps.setObject(1, java.sql.Timestamp.from(lastSyncTs)),
                (rs, rowNum) -> mapDoc(rs)
            );
            if (!docs.isEmpty()) {
                esRepo.saveAll(docs);
                log.info("ES delta sync : {} restaurants updated since {}", docs.size(), lastSyncTs);
            }
            lastSyncTs = now;
        } catch (Exception e) {
            log.warn("ES delta sync failed : {}", e.getMessage());
        }
    }

    /** Full bulk reindex — utilisé au démarrage et déclenchable manuellement via API admin. */
    public int bulkReindex() {
        List<RestaurantEsDoc> all = jdbc.query(
            """
            SELECT id, tenant_id, name, city, address, description, phone, status, updated_at
            FROM restaurants
            WHERE deleted_at IS NULL
            """,
            (rs, rowNum) -> mapDoc(rs)
        );
        // Batched save (500 par paquet) pour éviter heap pression sur gros datasets
        int batchSize = 500;
        for (int i = 0; i < all.size(); i += batchSize) {
            int end = Math.min(i + batchSize, all.size());
            esRepo.saveAll(all.subList(i, end));
        }
        lastSyncTs = java.time.Instant.now();
        return all.size();
    }

    private RestaurantEsDoc mapDoc(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID tenantId = rs.getObject("tenant_id", UUID.class);
        return new RestaurantEsDoc(
            id, tenantId,
            rs.getString("name"),
            rs.getString("city"),
            rs.getString("address"),
            rs.getString("description"),
            rs.getString("phone"),
            rs.getString("status")
        );
    }
}
