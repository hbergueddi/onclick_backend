package com.onesley.oneclick.etl.steps;

import com.onesley.oneclick.etl.EtlStep;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step 4 — Restaurants (1042) + Zones + Tables + Services + Staff (16577) + BusinessHours.
 *
 * <p>Mapping :
 * <ul>
 *   <li>{@code legacy.restaurants} → {@code restaurants} (drop columns Google, search_vector, tags, referral_*, etc.)</li>
 *   <li>3 restaurants legacy sans tenant_id → assignés au tenant OneClick par défaut</li>
 *   <li>{@code legacy.restaurant_staff} → {@code restaurant_staffs} (rename pluriel)</li>
 *   <li>{@code legacy.restaurant_zones} : columns `type`, `description`, `capacite`, `status` ignorées</li>
 *   <li>{@code legacy.restaurant_tables} : `numero` (int) → `table_number` (text), `capacite` → `seats`</li>
 *   <li>{@code legacy.restaurant_services} : `heure_debut`/`heure_fin` (text "HH:MM") → `start_time`/`end_time` (time)</li>
 *   <li>{@code business_hours} : jsonb {@code legacy.restaurants.opening_hours} explosé en N rows (1 par jour défini)</li>
 * </ul>
 */
@Component
@Profile("etl")
public class RestaurantsStep extends EtlStep.AbstractEtlStep {

    /** Tenant par défaut pour les 3 restos sans tenant_id (slug=oneclick). */
    private static final String DEFAULT_TENANT_ID = "00000000-0000-0000-0000-000000000001";

    public RestaurantsStep(JdbcTemplate jdbc, TransactionTemplate tx) {
        super(jdbc, tx);
    }

    @Override public String getName() { return "restaurants+zones+tables+services+staff+businessHours"; }

    @Override
    public String[] getTargetTables() {
        // FK reverse order
        return new String[] {
            "business_hours", "restaurant_staffs",
            "restaurant_services", "restaurant_tables", "restaurant_zones",
            "restaurants"
        };
    }

    @Override
    public long migrate() {
        long total = 0;

        // ─── restaurants (1042) ──────────────────────────────────────────────
        // legacy.status : 'actif' (1035) / 'inactif' (6) / 'prospect' (1) → mapping EN
        long r = jdbc.update("""
            INSERT INTO restaurants (id, tenant_id, name, description, phone, address, city,
                                     latitude, longitude, status, created_at, updated_at)
            SELECT
              r.id,
              COALESCE(r.tenant_id, ?::uuid),
              r.name,
              r.description,
              r.phone,
              r.address,
              COALESCE(NULLIF(r.city, ''), 'Casablanca'),
              r.latitude,
              r.longitude,
              CASE
                WHEN r.status IN ('actif', 'active') THEN 'active'
                WHEN r.status IN ('paused', 'pause') THEN 'paused'
                WHEN r.status IN ('inactif', 'archived', 'archive', 'prospect') THEN 'archived'
                ELSE 'active'
              END,
              COALESCE(r.created_at, now()),
              COALESCE(r.updated_at, now())
            FROM legacy.restaurants r
            """, DEFAULT_TENANT_ID);
        log.info("  restaurants : {} rows", r);
        total += r;

        // ─── restaurant_zones ────────────────────────────────────────────────
        long rz = jdbc.update("""
            INSERT INTO restaurant_zones (id, restaurant_id, name, created_at, updated_at)
            SELECT
              rz.id,
              rz.restaurant_id,
              COALESCE(NULLIF(rz.name, ''), 'Zone principale'),
              COALESCE(rz.created_at, now()),
              COALESCE(rz.updated_at, now())
            FROM legacy.restaurant_zones rz
            WHERE EXISTS (SELECT 1 FROM restaurants r WHERE r.id = rz.restaurant_id)
            """);
        log.info("  restaurant_zones : {} rows", rz);
        total += rz;

        // ─── restaurant_tables ───────────────────────────────────────────────
        // legacy.numero (int) → table_number (text), legacy.capacite (int) → seats (int)
        long rt = jdbc.update("""
            INSERT INTO restaurant_tables (id, zone_id, table_number, seats, created_at, updated_at)
            SELECT
              rt.id,
              rt.zone_id,
              COALESCE(rt.numero::text, 'T?'),
              COALESCE(rt.capacite, 4),
              COALESCE(rt.created_at, now()),
              COALESCE(rt.updated_at, now())
            FROM legacy.restaurant_tables rt
            WHERE EXISTS (SELECT 1 FROM restaurant_zones rz WHERE rz.id = rt.zone_id)
            """);
        log.info("  restaurant_tables : {} rows", rt);
        total += rt;

        // ─── restaurant_services (3141) ──────────────────────────────────────
        // legacy.heure_debut/heure_fin (text "HH:MM") → start_time/end_time (time)
        long rs = jdbc.update("""
            INSERT INTO restaurant_services (id, restaurant_id, name, start_time, end_time, created_at, updated_at)
            SELECT
              rs.id,
              rs.restaurant_id,
              COALESCE(NULLIF(rs.name, ''), 'Service'),
              CASE WHEN rs.heure_debut ~ '^[0-9]{1,2}:[0-9]{2}'
                   THEN rs.heure_debut::time
                   ELSE '12:00'::time END,
              CASE WHEN rs.heure_fin ~ '^[0-9]{1,2}:[0-9]{2}'
                   THEN rs.heure_fin::time
                   ELSE '14:00'::time END,
              COALESCE(rs.created_at, now()),
              COALESCE(rs.updated_at, now())
            FROM legacy.restaurant_services rs
            WHERE EXISTS (SELECT 1 FROM restaurants r WHERE r.id = rs.restaurant_id)
            """);
        log.info("  restaurant_services : {} rows", rs);
        total += rs;

        // ─── restaurant_staffs (16577) — rename pluriel ──────────────────────
        // legacy.staff_role (enum) → role_code (text)
        long rst = jdbc.update("""
            INSERT INTO restaurant_staffs (id, restaurant_id, user_id, role_code, created_at, updated_at)
            SELECT
              rs.id,
              rs.restaurant_id,
              rs.user_id,
              rs.staff_role::text,
              COALESCE(rs.created_at, now()),
              COALESCE(rs.created_at, now())
            FROM legacy.restaurant_staff rs
            WHERE EXISTS (SELECT 1 FROM restaurants r WHERE r.id = rs.restaurant_id)
              AND EXISTS (SELECT 1 FROM users u WHERE u.id = rs.user_id)
            """);
        log.info("  restaurant_staffs : {} rows", rst);
        total += rst;

        // ─── business_hours : explode opening_hours jsonb ────────────────────
        // legacy : array [{day: 0..6, open: "HH:MM", close: "HH:MM"}, ...]
        // enterprise : 1 row par (restaurant, day_of_week)
        //
        // Filtres :
        // - day_of_week 0..6 (CHECK enterprise)
        // - end_time > start_time (CHECK enterprise) → skip rows à 24h ou malformées
        // - "00:00" close interprété comme minuit fin → on remplace par '23:59'
        long bh = jdbc.update("""
            WITH parsed AS (
              SELECT
                r.id AS restaurant_id,
                (slot->>'day')::int AS day,
                CASE WHEN (slot->>'open') ~ '^[0-9]{1,2}:[0-9]{2}'
                     THEN (slot->>'open')::time
                     ELSE '09:00'::time END AS start_time,
                CASE
                  WHEN (slot->>'close') = '00:00' THEN '23:59'::time  -- minuit = fin de journée
                  WHEN (slot->>'close') ~ '^[0-9]{1,2}:[0-9]{2}'
                       THEN (slot->>'close')::time
                  ELSE '22:00'::time
                END AS end_time
              FROM legacy.restaurants r,
                   jsonb_array_elements(COALESCE(r.opening_hours, '[]'::jsonb)) slot
              WHERE EXISTS (SELECT 1 FROM restaurants e WHERE e.id = r.id)
                AND r.opening_hours IS NOT NULL
                AND jsonb_typeof(r.opening_hours) = 'array'
                AND slot->>'day' IS NOT NULL
                AND (slot->>'day')::int BETWEEN 0 AND 6
            )
            INSERT INTO business_hours (id, entity_type, entity_id, day_of_week, start_time, end_time, created_at, updated_at)
            SELECT
              gen_random_uuid(), 'restaurant', restaurant_id, day, start_time, end_time, now(), now()
            FROM parsed
            WHERE end_time > start_time
            """);
        log.info("  business_hours : {} rows (exploded from jsonb)", bh);
        total += bh;

        return total;
    }

    @Override
    public void validate() {
        long restaurants = countTarget("restaurants");
        long legacy = countLegacy("restaurants");
        if (restaurants != legacy) {
            throw new IllegalStateException("restaurants : enterprise=" + restaurants + " != legacy=" + legacy);
        }

        long staffs = countTarget("restaurant_staffs");
        long staffLegacy = countLegacy("restaurant_staff");
        if (staffs < staffLegacy * 95 / 100) { // 95% tolerance pour user_id orphelins
            throw new IllegalStateException(
                "restaurant_staffs : enterprise=" + staffs + " vs legacy=" + staffLegacy + " (loss > 5%)");
        }
    }
}
