package com.onesley.oneclick.etl;

import com.onesley.oneclick.etl.steps.CommerceStep;
import com.onesley.oneclick.etl.steps.LoyaltyStep;
import com.onesley.oneclick.etl.steps.PhaseBStep;
import com.onesley.oneclick.etl.steps.ReservationsStep;
import com.onesley.oneclick.etl.steps.RestaurantsStep;
import com.onesley.oneclick.etl.steps.RolesStep;
import com.onesley.oneclick.etl.steps.TenantsStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.CacheManager;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrateur ETL — exécute les steps dans l'ordre, gère TRUNCATE + setup + report.
 *
 * <p>Activé uniquement quand le profile {@code etl} est actif. Boot Spring sans web,
 * applique l'ETL, puis ferme l'application context (exit 0).
 *
 * <p>Run : {@code SPRING_PROFILES_ACTIVE=enterprise,etl ./mvnw spring-boot:run}
 */
@Component
@Profile("etl")
public class EtlOrchestrator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EtlOrchestrator.class);

    private final JdbcTemplate jdbc;
    private final EtlProperties props;
    private final EtlPostgresFdwBootstrap fdw;
    private final ConfigurableApplicationContext ctx;
    private final ObjectProvider<CacheManager> cacheManagerProvider;

    // Phase A steps
    private final TenantsStep tenantsStep;
    private final RolesStep rolesStep;
    // Note : UsersStep + RestaurantsStep ne sont pas directement injectés ici car les types
    // concrets ne nous intéressent pas — on les charge par @Autowired sur List<EtlStep>.
    // Mais pour préserver l'ordre, on déclare explicitement la liste.
    private final List<EtlStep> phaseASteps;
    private final List<EtlStep> phaseBSteps;

    public EtlOrchestrator(JdbcTemplate jdbc,
                           EtlProperties props,
                           EtlPostgresFdwBootstrap fdw,
                           ConfigurableApplicationContext ctx,
                           ObjectProvider<CacheManager> cacheManagerProvider,
                           TenantsStep tenantsStep,
                           RolesStep rolesStep,
                           com.onesley.oneclick.etl.steps.UsersStep usersStep,
                           RestaurantsStep restaurantsStep,
                           ReservationsStep reservationsStep,
                           LoyaltyStep loyaltyStep,
                           CommerceStep commerceStep,
                           PhaseBStep phaseBStep) {
        this.jdbc = jdbc;
        this.props = props;
        this.fdw = fdw;
        this.ctx = ctx;
        this.cacheManagerProvider = cacheManagerProvider;
        this.tenantsStep = tenantsStep;
        this.rolesStep = rolesStep;
        // Order MATTERS — FK dependencies
        this.phaseASteps = List.of(
            tenantsStep,        // tenants, tenant_brandings, tenant_features, company_settings
            rolesStep,          // roles
            usersStep,          // users (dépend de tenants + roles)
            restaurantsStep,    // restaurants, staff, zones, tables, services, business_hours
            reservationsStep,   // reservations + guests + booking_rules
            loyaltyStep,        // tiers, loyalty_accounts, loyalty_transactions, redemptions, loyalty_rules
            commerceStep        // offers, contracts, invoices, invoice_lines, wallet_transactions
        );
        this.phaseBSteps = List.of(
            phaseBStep          // notifications, device_tokens, friendships, referrals, support_tickets,
                                // events, event_participations, resources, resource_bookings, audit_logs
        );
    }

    @Override
    public void run(String... args) {
        log.info("════════════════════════════════════════════════════════════════════");
        log.info(" Phase 13 ETL — démarrage");
        log.info("════════════════════════════════════════════════════════════════════");
        Instant t0 = Instant.now();
        int exitCode = 0;

        try {
            // 1. Setup postgres_fdw
            fdw.setup();

            // 2. TRUNCATE des tables cibles (reverse FK order across all steps)
            if (props.isTruncateBeforeEtl()) {
                truncateAll();
            }

            // 3. Phase A — Critical path
            Map<String, Long> results = new LinkedHashMap<>();
            if (props.isRunPhaseA()) {
                log.info("");
                log.info("───────────────────── Phase 13.A — Critical path ─────────────────────");
                for (EtlStep step : phaseASteps) {
                    if (step instanceof EtlStep.AbstractEtlStep abstractStep) {
                        long inserted = abstractStep.run();
                        results.put(step.getName(), inserted);
                        step.validate();
                    }
                }
            }

            // 4. Phase B — Secondaire (notifications, social, audit, ...)
            if (props.isRunPhaseB()) {
                log.info("");
                log.info("───────────────────── Phase 13.B — Secondaire ─────────────────────");
                for (EtlStep step : phaseBSteps) {
                    if (step instanceof EtlStep.AbstractEtlStep abstractStep) {
                        long inserted = abstractStep.run();
                        results.put(step.getName(), inserted);
                        step.validate();
                    }
                }
            }

            // 5. Flush du cache Redis pour éviter les entrées périmées
            // (TRUNCATE+INSERT bypass @CacheEvict des services Spring)
            flushCaches();

            // 6. Rapport final
            Duration total = Duration.between(t0, Instant.now());
            log.info("");
            log.info("════════════════════════════════════════════════════════════════════");
            log.info(" Phase 13 ETL — terminé en {} s", total.toSeconds());
            log.info("════════════════════════════════════════════════════════════════════");
            results.forEach((step, count) -> log.info("  {} : {} rows", step, count));
            log.info("  TOTAL : {} rows insérées", results.values().stream().mapToLong(Long::longValue).sum());

        } catch (Exception e) {
            log.error("Phase 13 ETL — ÉCHEC : {}", e.getMessage(), e);
            exitCode = 1;
        } finally {
            // Cleanup foreign tables (optionnel — on garde pour debug)
            // fdw.teardown();

            // Exit Spring Boot
            int code = exitCode;
            new Thread(() -> {
                ctx.close();
                System.exit(code);
            }).start();
        }
    }

    /**
     * Flush tous les caches Spring (Redis) — nécessaire post-ETL car le TRUNCATE direct
     * en SQL bypass les annotations @CacheEvict des services Spring. Sans flush, l'app
     * sert des UserDto / TenantDto / RestaurantDto périmés (rows déjà supprimées).
     */
    private void flushCaches() {
        CacheManager cm = cacheManagerProvider.getIfAvailable();
        if (cm == null) {
            log.warn("CacheManager indisponible — skip flush");
            return;
        }
        log.info("");
        log.info("───────────────────── Flush des caches Redis ─────────────────────");
        for (String name : cm.getCacheNames()) {
            var cache = cm.getCache(name);
            if (cache != null) {
                cache.clear();
                log.debug("  ✓ Cache cleared : {}", name);
            }
        }
        log.info("  ✓ {} caches flushed", cm.getCacheNames().size());
    }

    /**
     * TRUNCATE toutes les tables cibles dans l'ordre inverse des FK pour éviter
     * les violations de contraintes. CASCADE pour balayer les dépendances dynamiques.
     */
    private void truncateAll() {
        log.info("");
        log.info("───────────────────── TRUNCATE des tables cibles ─────────────────────");

        // Liste exhaustive Phase A + B — ordre inverse FK
        List<String> tables = List.of(
            // ── Phase B : tables très filles (filles de Phase A) ─────────────
            "audit_logs",
            "resource_bookings",
            "resources",
            "event_participations",
            "events",
            "support_tickets",
            "referrals",
            "friendships",
            "device_tokens",
            "notifications",
            // ── Phase A : tables très filles ─────────────────────────────────
            "invoice_lines",
            "redemptions",
            "loyalty_transactions",
            "reservation_guests",
            "business_hours",
            "restaurant_staffs",
            "restaurant_tables",
            // ── Phase A : tables filles ──────────────────────────────────────
            "wallet_transactions",
            "invoices",
            "contracts",
            "offers",
            "loyalty_accounts",
            "loyalty_rules",
            "booking_rules",
            "reservations",
            "restaurant_zones",
            "restaurant_services",
            "permissions",
            // ── Phase A : tables intermédiaires ──────────────────────────────
            "tiers",
            "users",
            "restaurants",
            // ── Phase A : tables racines ─────────────────────────────────────
            "menus",
            "actions",
            "roles",
            "company_settings",
            "tenant_features",
            "tenant_brandings",
            "tenants"
        );

        // Un seul TRUNCATE ... CASCADE pour optimiser
        String sql = "TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE";
        log.debug("  TRUNCATE: {}", String.join(", ", tables));
        jdbc.execute(sql);
        log.info("  ✓ {} tables truncated", tables.size());
    }
}
