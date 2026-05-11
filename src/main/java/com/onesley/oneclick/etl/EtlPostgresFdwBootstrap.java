package com.onesley.oneclick.etl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Setup {@code postgres_fdw} dans la DB enterprise pour lire les données legacy.
 *
 * <p>Idempotent : utilise {@code CREATE EXTENSION/SERVER/SCHEMA IF NOT EXISTS},
 * et drop+recreate les foreign tables (le schéma legacy peut évoluer, on remappe à chaque ETL).
 *
 * <p>Architecture :
 * <pre>
 * oneclick_enterprise (target DB)
 *   ├── schema public/      : tables enterprise (users, restaurants, ...)
 *   └── schema legacy/      : foreign tables → oneclick_local.public.*
 *       │
 *       └── postgres_fdw    : connecteur natif PG
 *               │
 *               └────────► oneclick_local DB
 * </pre>
 */
@Component
@Profile("etl")
public class EtlPostgresFdwBootstrap {

    private static final Logger log = LoggerFactory.getLogger(EtlPostgresFdwBootstrap.class);

    private static final String SERVER_NAME = "oneclick_legacy_server";
    private static final String FOREIGN_SCHEMA = "legacy";

    /**
     * Tables à importer depuis legacy.public — Phase A uniquement.
     *
     * <p>Les tables Phase B (notifications, friendships, bookable_resources, etc.) sont importées
     * séparément quand on activera Phase B (cf {@link #importPhaseBTables()}). Ceci évite les
     * erreurs IMPORT FOREIGN SCHEMA dues aux types enum custom legacy (bookable_resource_type, etc.)
     * non présents en enterprise.
     */
    private static final List<String> LEGACY_TABLES_PHASE_A = List.of(
        // Tenants & config
        "tenants", "tenant_branding", "tenant_features", "company_settings",
        // Identity
        "profiles", "user_roles",
        // Restaurant
        "restaurants", "restaurant_staff", "restaurant_zones", "restaurant_tables", "restaurant_services",
        "booking_rules",
        // Reservation
        "reservations", "reservation_guests",
        // Loyalty
        "loyalty_points", "redemption_events", "tier_thresholds", "restaurant_tier_config",
        "gain_rules", "restaurant_gain_rules",
        // Promotion
        "offers",
        // Financial
        "partner_contracts", "oneclick_hi_invoices", "invoice_lines",
        "admin_wallet_transactions"
    );

    /** Tables Phase B — importées seulement si runPhaseB=true. */
    private static final List<String> LEGACY_TABLES_PHASE_B = List.of(
        "notifications", "device_tokens", "friendships", "referrals",
        "support_tickets", "tenant_events", "event_rsvps",
        // bookable_resources skipped — enum type custom non portable (à recréer manuellement)
        "resource_bookings",
        "action_logs", "admin_audit_log"
    );

    /** Tables du schéma {@code auth} (Supabase managed) à importer séparément. */
    private static final List<String> LEGACY_AUTH_TABLES = List.of("users");

    private final JdbcTemplate jdbc;
    private final EtlProperties props;

    public EtlPostgresFdwBootstrap(JdbcTemplate jdbc, EtlProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public void setup() {
        log.info("Phase 13 ETL — setup postgres_fdw pour DB legacy {}:{}/{}",
            props.getLegacy().getHost(), props.getLegacy().getPort(), props.getLegacy().getDatabase());

        jdbc.execute("CREATE EXTENSION IF NOT EXISTS postgres_fdw");

        // Drop si existe pour reset (server is owner of user mapping)
        jdbc.execute("DROP SERVER IF EXISTS " + SERVER_NAME + " CASCADE");

        jdbc.execute(String.format(
            "CREATE SERVER %s FOREIGN DATA WRAPPER postgres_fdw " +
            "OPTIONS (host '%s', port '%d', dbname '%s')",
            SERVER_NAME,
            props.getLegacy().getHost(),
            props.getLegacy().getPort(),
            props.getLegacy().getDatabase()));

        jdbc.execute(String.format(
            "CREATE USER MAPPING FOR CURRENT_USER SERVER %s OPTIONS (user '%s', password '%s')",
            SERVER_NAME,
            props.getLegacy().getUser(),
            props.getLegacy().getPassword()));

        // Schéma local pour héberger les foreign tables
        jdbc.execute("DROP SCHEMA IF EXISTS " + FOREIGN_SCHEMA + " CASCADE");
        jdbc.execute("CREATE SCHEMA " + FOREIGN_SCHEMA);

        // ─── Pré-création des enum types custom legacy ────────────────────────
        // IMPORT FOREIGN SCHEMA crée les foreign tables avec les types EXACTS du legacy.
        // Les enums custom doivent donc exister localement en enterprise pour que l'IMPORT
        // marche. On les crée avec les mêmes valeurs que legacy (cf audit ETL #5).
        createEnumIfMissing("reservation_status",
            "'demandée','confirmée','placée','terminée','annulée','honorée','no_show','refusée','contre_proposition','en_attente'");
        createEnumIfMissing("staff_role",
            "'owner','manager','waiter','serveur','chef_de_rang','barman','caissier','controleur','responsable_resa','directeur'");
        createEnumIfMissing("app_role",
            "'admin','restaurateur','client','tenant_admin'");

        // Import des tables du schéma public legacy — Phase A
        String publicImport = String.format(
            "IMPORT FOREIGN SCHEMA public LIMIT TO (%s) FROM SERVER %s INTO %s",
            String.join(", ", LEGACY_TABLES_PHASE_A),
            SERVER_NAME,
            FOREIGN_SCHEMA);
        log.debug("Importing legacy.public tables (Phase A): {}", LEGACY_TABLES_PHASE_A.size());
        jdbc.execute(publicImport);

        // Import du schéma auth (Supabase managed)
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + FOREIGN_SCHEMA + "_auth");
        jdbc.execute(String.format(
            "IMPORT FOREIGN SCHEMA auth LIMIT TO (%s) FROM SERVER %s INTO %s",
            String.join(", ", LEGACY_AUTH_TABLES),
            SERVER_NAME,
            FOREIGN_SCHEMA + "_auth"));

        // Verify
        Long publicCount = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.foreign_tables WHERE foreign_table_schema=?",
            Long.class, FOREIGN_SCHEMA);
        Long authCount = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.foreign_tables WHERE foreign_table_schema=?",
            Long.class, FOREIGN_SCHEMA + "_auth");

        log.info("postgres_fdw setup terminé : {} foreign tables dans legacy.* + {} dans legacy_auth.*",
            publicCount, authCount);

        // Smoke test : count des profiles legacy
        Long profilesCount = jdbc.queryForObject("SELECT count(*) FROM legacy.profiles", Long.class);
        Long authUsersCount = jdbc.queryForObject("SELECT count(*) FROM legacy_auth.users", Long.class);
        log.info("legacy.profiles = {} rows, legacy_auth.users = {} rows", profilesCount, authUsersCount);
    }

    public void teardown() {
        log.info("Phase 13 ETL — cleanup postgres_fdw");
        jdbc.execute("DROP SCHEMA IF EXISTS " + FOREIGN_SCHEMA + " CASCADE");
        jdbc.execute("DROP SCHEMA IF EXISTS " + FOREIGN_SCHEMA + "_auth CASCADE");
        jdbc.execute("DROP SERVER IF EXISTS " + SERVER_NAME + " CASCADE");
    }

    /**
     * Crée un type ENUM en {@code public} si absent (les foreign tables le référencent).
     * Idempotent : DO block + EXCEPTION handling sur duplicate_object.
     */
    private void createEnumIfMissing(String typeName, String values) {
        jdbc.execute(String.format("""
            DO $$
            BEGIN
              IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = '%s') THEN
                CREATE TYPE %s AS ENUM (%s);
              END IF;
            END $$;
            """, typeName, typeName, values));
    }
}
