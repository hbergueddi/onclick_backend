package com.onesley.oneclick.etl.steps;

import com.onesley.oneclick.etl.EtlStep;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Step 5 — Reservations (3552) + ReservationGuests (194) + BookingRules.
 *
 * <p>Transformations clés :
 * <ul>
 *   <li>{@code legacy.date + heure} (text "HH:MM") → {@code reservation_at} timestamptz</li>
 *   <li>{@code legacy.status} enum FR → enterprise text EN avec CHECK contraint :
 *     <ul>
 *       <li>demandée / en_attente → pending</li>
 *       <li>confirmée → confirmed</li>
 *       <li>refusée → refused</li>
 *       <li>contre_proposition → counter_proposed</li>
 *       <li>annulée → cancelled</li>
 *       <li>honorée / placée / terminée → honored</li>
 *       <li>no_show → no_show</li>
 *     </ul>
 *   </li>
 *   <li>{@code tenant_id} : lookup via restaurant (legacy n'a pas la colonne)</li>
 *   <li>{@code couverts} → {@code guest_count} (CHECK > 0, donc COALESCE à 1)</li>
 *   <li>{@code table_id / service_id} : NULL (legacy stocke `zone`/`table_num`/`service` en text)</li>
 * </ul>
 *
 * <p>Booking rules : refonte complète. Legacy = key/value catalog. Enterprise = colonnes
 * structurées. → On crée 1 row par restaurant avec valeurs par défaut sensées
 * (max_guest=20, slot_duration=90 min, cancellation=24h).
 */
@Component
@Profile("etl")
public class ReservationsStep extends EtlStep.AbstractEtlStep {

    public ReservationsStep(JdbcTemplate jdbc, TransactionTemplate tx) {
        super(jdbc, tx);
    }

    @Override public String getName() { return "reservations+guests+booking_rules"; }

    @Override
    public String[] getTargetTables() {
        return new String[] { "reservation_guests", "reservations", "booking_rules" };
    }

    @Override
    public long migrate() {
        long total = 0;

        // ─── reservations (3552) ─────────────────────────────────────────────
        long resv = jdbc.update("""
            INSERT INTO reservations (id, tenant_id, client_id, restaurant_id,
                                      reservation_at, guest_count, status, notes,
                                      created_at, updated_at)
            SELECT
              r.id,
              er.tenant_id,
              r.client_id,
              r.restaurant_id,
              -- date (date) + heure (text "HH:MM") → timestamptz
              CASE
                WHEN r.heure ~ '^[0-9]{1,2}:[0-9]{2}'
                  THEN (r.date::text || ' ' || r.heure)::timestamptz
                ELSE (r.date::text || ' 12:00')::timestamptz
              END AS reservation_at,
              GREATEST(COALESCE(r.couverts, 1), 1) AS guest_count,
              CASE r.status::text
                WHEN 'demandée'            THEN 'pending'
                WHEN 'en_attente'          THEN 'pending'
                WHEN 'confirmée'           THEN 'confirmed'
                WHEN 'placée'              THEN 'honored'
                WHEN 'terminée'            THEN 'honored'
                WHEN 'honorée'             THEN 'honored'
                WHEN 'no_show'             THEN 'no_show'
                WHEN 'refusée'             THEN 'refused'
                WHEN 'contre_proposition'  THEN 'counter_proposed'
                WHEN 'annulée'             THEN 'cancelled'
                ELSE 'pending'
              END AS status,
              r.notes,
              COALESCE(r.created_at, now()),
              COALESCE(r.updated_at, now())
            FROM legacy.reservations r
            -- Join enterprise.restaurants pour récupérer tenant_id
            JOIN restaurants er ON er.id = r.restaurant_id
            WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = r.client_id)
            """);
        log.info("  reservations : {} rows (drop si client_id orphelin)", resv);
        total += resv;

        // ─── reservation_guests (194) ────────────────────────────────────────
        // enterprise.reservation_guests : pas de updated_at (table de jonction)
        long guests = jdbc.update("""
            INSERT INTO reservation_guests (id, reservation_id, guest_user_id, guest_name, created_at)
            SELECT
              rg.id,
              rg.reservation_id,
              -- Skip guest_user_id si user orphelin (legacy avait des refs cassées)
              CASE WHEN EXISTS (SELECT 1 FROM users u WHERE u.id = rg.guest_user_id) THEN rg.guest_user_id ELSE NULL END,
              rg.guest_name,
              COALESCE(rg.created_at, now())
            FROM legacy.reservation_guests rg
            WHERE EXISTS (SELECT 1 FROM reservations r WHERE r.id = rg.reservation_id)
            """);
        log.info("  reservation_guests : {} rows", guests);
        total += guests;

        // ─── booking_rules : refonte structurée (1 row par restaurant) ───────
        // Legacy = catalogue key/value (7294 rows). Enterprise = 3 colonnes typées.
        // Stratégie : 1 row par restaurant avec defaults sensés. Pas de mapping
        // depuis legacy (structures incompatibles).
        long br = jdbc.update("""
            INSERT INTO booking_rules (id, restaurant_id, max_guest, slot_duration, cancellation_window_hours,
                                       created_at, updated_at)
            SELECT
              gen_random_uuid(),
              r.id,
              20,   -- max_guest par défaut
              90,   -- slot_duration en minutes
              24,   -- cancellation_window_hours
              now(),
              now()
            FROM restaurants r
            """);
        log.info("  booking_rules : {} rows (1 par restaurant, defaults — legacy structure incompatible)", br);
        total += br;

        return total;
    }

    @Override
    public void validate() {
        long resv = countTarget("reservations");
        long legacyResv = countLegacy("reservations");
        // Tolerance 5% pour les reservations avec client orphelin (skippées)
        if (resv < legacyResv * 95 / 100) {
            throw new IllegalStateException(
                "reservations : enterprise=" + resv + " vs legacy=" + legacyResv + " (perte > 5%)");
        }
    }
}
