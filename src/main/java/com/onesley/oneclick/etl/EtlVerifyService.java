package com.onesley.oneclick.etl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint G.7 — Verify ETL counts : compare source legacy vs target enterprise
 * pour chaque table critique. Rapporte les écarts (volumétrie) sans bloquer
 * le pipeline (warning only).
 *
 * <p>Mapping table source → target — certaines tables fusionnent (ex: scanned_tickets
 * + loyalty_points legacy → loyalty_transactions enterprise) donc on compare
 * intelligemment selon les règles métier (cf docs/PHASE13-ETL-MAPPING.md).
 */
@Component
@Profile("etl")
public class EtlVerifyService {

    private static final Logger log = LoggerFactory.getLogger(EtlVerifyService.class);

    private final JdbcTemplate jdbc;

    /**
     * Mapping count source (legacy schema via FDW) → target (enterprise).
     *
     * <p>Format : {nom_lisible, sourceQuery, targetQuery, toleranceMode}.
     * tolerance "exact" : counts doivent être identiques.
     * tolerance "subset" : target peut être >= source (cas merge multi-source).
     */
    private static final List<Mapping> MAPPINGS = List.of(
        new Mapping("tenants",             "SELECT COUNT(*) FROM legacy.tenants",                      "SELECT COUNT(*) FROM tenants",                    "exact"),
        new Mapping("users (= profiles)",  "SELECT COUNT(*) FROM legacy.profiles",                     "SELECT COUNT(*) FROM users",                       "exact"),
        new Mapping("roles",               "SELECT COUNT(DISTINCT role) FROM legacy.user_roles",       "SELECT COUNT(*) FROM roles",                       "subset"),
        new Mapping("restaurants",         "SELECT COUNT(*) FROM legacy.restaurants",                  "SELECT COUNT(*) FROM restaurants",                 "exact"),
        new Mapping("restaurant_staff",    "SELECT COUNT(*) FROM legacy.restaurant_staff",             "SELECT COUNT(*) FROM restaurant_staffs",           "exact"),
        new Mapping("reservations",        "SELECT COUNT(*) FROM legacy.reservations",                 "SELECT COUNT(*) FROM reservations",                "exact"),
        new Mapping("reservation_guests",  "SELECT COUNT(*) FROM legacy.reservation_guests",           "SELECT COUNT(*) FROM reservation_guests",          "exact"),
        new Mapping("loyalty_accounts",    "SELECT COUNT(DISTINCT (client_id, restaurant_id)) FROM legacy.loyalty_points",
                                            "SELECT COUNT(*) FROM loyalty_accounts",                    "subset"),
        new Mapping("loyalty_txs (= loyalty_points + scanned_tickets)",
                                            "SELECT (SELECT COUNT(*) FROM legacy.loyalty_points) + (SELECT COUNT(*) FROM legacy.scanned_tickets)",
                                            "SELECT COUNT(*) FROM loyalty_transactions",                "subset"),
        new Mapping("offers",              "SELECT COUNT(*) FROM legacy.offers",                       "SELECT COUNT(*) FROM offers",                      "exact"),
        new Mapping("contracts",           "SELECT COUNT(*) FROM legacy.partner_contracts",            "SELECT COUNT(*) FROM contracts",                   "exact"),
        new Mapping("notifications",       "SELECT COUNT(*) FROM legacy.notifications",                "SELECT COUNT(*) FROM notifications",               "exact"),
        new Mapping("device_tokens",       "SELECT COUNT(*) FROM legacy.device_tokens",                "SELECT COUNT(*) FROM device_tokens",               "exact"),
        new Mapping("friendships",         "SELECT COUNT(*) FROM legacy.friendships",                  "SELECT COUNT(*) FROM friendships",                 "exact"),
        new Mapping("referrals",           "SELECT COUNT(*) FROM legacy.referrals",                    "SELECT COUNT(*) FROM referrals",                   "exact"),
        new Mapping("support_tickets",     "SELECT COUNT(*) FROM legacy.support_tickets",              "SELECT COUNT(*) FROM support_tickets",             "exact"),
        new Mapping("audit_logs",          "SELECT COUNT(*) FROM legacy.action_logs",                  "SELECT COUNT(*) FROM audit_logs",                  "exact")
    );

    public EtlVerifyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Exécute la vérification et retourne un rapport structuré.
     * Pas de throw — les écarts sont logués WARN, à toi de juger si bloquant.
     */
    public Map<String, VerifyResult> verify() {
        log.info("");
        log.info("───────────────────── Verify counts (Sprint G.7) ─────────────────────");
        Map<String, VerifyResult> results = new LinkedHashMap<>();
        int okCount = 0;
        int warnCount = 0;
        int errorCount = 0;

        for (Mapping m : MAPPINGS) {
            try {
                long sourceCount = countSafe(m.sourceQuery);
                long targetCount = countSafe(m.targetQuery);
                Status status = compareCounts(sourceCount, targetCount, m.tolerance);
                VerifyResult vr = new VerifyResult(m.name, sourceCount, targetCount, status, null);
                results.put(m.name, vr);

                switch (status) {
                    case OK -> {
                        log.info("  ✓ {} : source={}, target={}", m.name, sourceCount, targetCount);
                        okCount++;
                    }
                    case WARN -> {
                        log.warn("  ⚠ {} : source={}, target={} (delta {})",
                            m.name, sourceCount, targetCount, targetCount - sourceCount);
                        warnCount++;
                    }
                    case MISMATCH -> {
                        log.error("  ✗ {} : source={}, target={} — MISMATCH",
                            m.name, sourceCount, targetCount);
                        errorCount++;
                    }
                }
            } catch (Exception e) {
                log.warn("  ⚠ {} : ERROR — {}", m.name, e.getMessage());
                results.put(m.name, new VerifyResult(m.name, -1, -1, Status.MISMATCH, e.getMessage()));
                errorCount++;
            }
        }

        log.info("");
        log.info("Verify rapport : {} ✓ · {} ⚠ · {} ✗",
            okCount, warnCount, errorCount);
        return results;
    }

    private long countSafe(String sql) {
        Long count = jdbc.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }

    private Status compareCounts(long source, long target, String tolerance) {
        if (source == target) return Status.OK;
        if ("subset".equals(tolerance) && target >= source) return Status.OK;
        // Si target < source de moins de 5%, c'est WARN (cas dump partiel)
        if (target < source && Math.abs(target - source) <= source * 0.05) return Status.WARN;
        return Status.MISMATCH;
    }

    public record Mapping(String name, String sourceQuery, String targetQuery, String tolerance) {}
    public record VerifyResult(String name, long sourceCount, long targetCount, Status status, String error) {}
    public enum Status { OK, WARN, MISMATCH }
}
