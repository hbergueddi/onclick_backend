package com.onesley.oneclick.etl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * Étape ETL unitaire — chaque step migre 1 ou plusieurs tables enterprise depuis legacy.
 *
 * <p>Pattern : {@link #migrate()} fait l'INSERT, ensuite {@link #reportCount()} renvoie le
 * nombre de rows insérées (pour le rapport final). Les steps sont exécutées dans l'ordre
 * par {@link EtlOrchestrator}.
 *
 * <p>Implémentations concrètes via {@link AbstractEtlStep}.
 */
public interface EtlStep {

    /** Nom logique de l'étape (ex: "users", "restaurants"). */
    String getName();

    /** Tables enterprise à TRUNCATE avant migration (ordre inverse FK). */
    String[] getTargetTables();

    /** Exécute la migration. Retourne le nombre de rows insérées. */
    long migrate();

    /** Vérifications post-migration (counts, FK integrity). Throws en cas d'incohérence. */
    default void validate() {
        // Override si besoin
    }

    /**
     * Base class qui fournit {@code jdbc} + {@code tx} + logging timing.
     */
    abstract class AbstractEtlStep implements EtlStep {

        protected final Logger log;
        protected final JdbcTemplate jdbc;
        protected final TransactionTemplate tx;

        protected AbstractEtlStep(JdbcTemplate jdbc, TransactionTemplate tx) {
            this.jdbc = jdbc;
            this.tx = tx;
            this.log = LoggerFactory.getLogger(getClass());
        }

        /** Exécute la migration avec logging + timing autour. */
        public final long run() {
            log.info("▶ Step [{}] start", getName());
            Instant t0 = Instant.now();
            long inserted = migrate();
            Duration d = Duration.between(t0, Instant.now());
            log.info("✓ Step [{}] done — {} rows inserted in {} ms",
                getName(), inserted, d.toMillis());
            return inserted;
        }

        protected long countTarget(String tableName) {
            Long n = jdbc.queryForObject("SELECT count(*) FROM " + tableName, Long.class);
            return n == null ? 0L : n;
        }

        protected long countLegacy(String legacyTable) {
            Long n = jdbc.queryForObject("SELECT count(*) FROM legacy." + legacyTable, Long.class);
            return n == null ? 0L : n;
        }
    }
}
