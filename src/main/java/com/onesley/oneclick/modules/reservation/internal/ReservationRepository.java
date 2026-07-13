package com.onesley.oneclick.modules.reservation.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository {@link Reservation} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {
    java.util.List<Reservation> findAllByTenantId(java.util.UUID tenantId);
    java.util.List<Reservation> findAllByClientId(java.util.UUID clientId);

    /**
     * Résout le {@code tenant_id} d'un restaurant (SQL natif — le module lit déjà {@code restaurants}
     * via ses jointures). Sert à l'outil chatbot {@code book_reservation} pour fixer le tenant
     * <b>côté serveur</b> à partir du seul {@code restaurantId}, sans le faire fournir par le LLM.
     */
    @Query(value = "SELECT tenant_id FROM restaurants WHERE id = :restaurantId AND deleted_at IS NULL",
        nativeQuery = true)
    java.util.Optional<UUID> findTenantIdByRestaurantId(@Param("restaurantId") UUID restaurantId);

    /** Nom du restaurant (SQL natif) — pour le récapitulatif de confirmation de {@code book_reservation}. */
    @Query(value = "SELECT name FROM restaurants WHERE id = :restaurantId AND deleted_at IS NULL",
        nativeQuery = true)
    java.util.Optional<String> findRestaurantNameById(@Param("restaurantId") UUID restaurantId);
    java.util.List<Reservation> findAllByRestaurantId(java.util.UUID restaurantId);
    java.util.List<Reservation> findAllByTableId(java.util.UUID tableId);
    java.util.List<Reservation> findAllByServiceId(java.util.UUID serviceId);

    // ═══════════════════════════════════════════════════════════════════════
    //  Anti N+1 — réservations enrichies par jointures SQL
    // ═══════════════════════════════════════════════════════════════════════
    //
    // Contexte : le front Pocket (Compass / Vault / MyReservations) liste les
    // réservations et a besoin d'afficher pour chacune : prénom client, nom +
    // ville + visuel du restaurant, nom de table / zone / service, raisons
    // workflow. Avant cette projection, le client web faisait N+1 GET
    // /api/users/{clientId} + /api/restaurants/{restaurantId} par ligne.
    //
    // On reste au niveau SQL (table names) car les Entity {@code Restaurant},
    // {@code RestaurantTable}, {@code RestaurantZone} et {@code MealService}
    // sont dans {@code modules.restaurant.internal} (Modulith CLOSED interdit
    // l'import depuis modules.reservation).
    //
    // Les sous-requêtes corrélées sur {@code reservation_status_histories}
    // récupèrent la dernière {@code reason} pour les statuts {@code refused}
    // et {@code cancelled} — utile pour afficher "Refusée car..." côté Pocket.

    @Query(value = """
        SELECT
            r.id              AS id,
            r.tenant_id       AS tenantId,
            r.client_id       AS clientId,
            r.restaurant_id   AS restaurantId,
            r.table_id        AS tableId,
            r.service_id      AS serviceId,
            r.reservation_at  AS reservationAt,
            r.guest_count     AS guestCount,
            r.status          AS status,
            r.notes           AS notes,
            r.created_at      AS createdAt,
            r.late_cancellation AS lateCancellation,
            r.no_show_marked_at AS noShowMarkedAt,
            r.proposed_reservation_at AS proposedReservationAt,
            u.first_name      AS clientFirstName,
            u.last_name       AS clientLastName,
            u.phone           AS clientPhone,
            array_to_string(u.allergens, ',') AS clientAllergens,
            rest.name         AS restaurantName,
            rest.city         AS restaurantCity,
            rest.image        AS restaurantImage,
            rest.address          AS restaurantAddress,
            rest.latitude         AS restaurantLatitude,
            rest.longitude        AS restaurantLongitude,
            rest.google_place_id  AS restaurantGooglePlaceId,
            ms.name           AS mealServiceName,
            rz.name           AS zoneName,
            rt.table_number   AS tableNumber,
            (
                SELECT rsh.reason FROM reservation_status_histories rsh
                WHERE rsh.reservation_id = r.id AND rsh.new_status = 'refused'
                ORDER BY rsh.changed_at DESC LIMIT 1
            )                 AS refusalReason,
            (
                SELECT rsh.reason FROM reservation_status_histories rsh
                WHERE rsh.reservation_id = r.id AND rsh.new_status = 'cancelled'
                ORDER BY rsh.changed_at DESC LIMIT 1
            )                 AS cancellationReason
        FROM reservations r
        LEFT JOIN users               u    ON u.id    = r.client_id
        LEFT JOIN restaurants         rest ON rest.id = r.restaurant_id
        LEFT JOIN restaurant_tables   rt   ON rt.id   = r.table_id
        LEFT JOIN restaurant_zones    rz   ON rz.id   = rt.zone_id
        LEFT JOIN restaurant_services ms   ON ms.id   = r.service_id
        WHERE r.deleted_at IS NULL
          AND (CAST(:clientId     AS uuid) IS NULL OR r.client_id     = CAST(:clientId     AS uuid))
          AND (CAST(:restaurantId AS uuid) IS NULL OR r.restaurant_id = CAST(:restaurantId AS uuid))
          AND (:status IS NULL OR r.status = :status)
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR r.reservation_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo   AS timestamptz) IS NULL OR r.reservation_at <  CAST(:dateTo   AS timestamptz))
        ORDER BY r.reservation_at DESC
        """,
        countQuery = """
        SELECT COUNT(*)
        FROM reservations r
        WHERE r.deleted_at IS NULL
          AND (CAST(:clientId     AS uuid) IS NULL OR r.client_id     = CAST(:clientId     AS uuid))
          AND (CAST(:restaurantId AS uuid) IS NULL OR r.restaurant_id = CAST(:restaurantId AS uuid))
          AND (:status IS NULL OR r.status = :status)
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR r.reservation_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo   AS timestamptz) IS NULL OR r.reservation_at <  CAST(:dateTo   AS timestamptz))
        """,
        nativeQuery = true)
    Page<ReservationWithJoinsView> findAllWithJoins(
        @Param("clientId") UUID clientId,
        @Param("restaurantId") UUID restaurantId,
        @Param("status") String status,
        @Param("dateFrom") Instant dateFrom,
        @Param("dateTo") Instant dateTo,
        Pageable pageable
    );

    /**
     * P1 (anti-N+1 shim) — réservations enrichies de PLUSIEURS restaurants en 1 requête.
     * Remplace le fan-out {@code Promise.all(ids.map(findByRestaurant))} du shim Supabase
     * (client.ts:211) + PulsePro. Même projection enrichie que {@link #findAllWithJoins}
     * (LEFT JOIN users/restaurants/tables/zones/services + reason histories) ; seul le
     * filtre change : {@code restaurant_id IN (:restaurantIds)} + LIMIT global (récents d'abord).
     */
    @Query(value = """
        SELECT
            r.id              AS id,
            r.tenant_id       AS tenantId,
            r.client_id       AS clientId,
            r.restaurant_id   AS restaurantId,
            r.table_id        AS tableId,
            r.service_id      AS serviceId,
            r.reservation_at  AS reservationAt,
            r.guest_count     AS guestCount,
            r.status          AS status,
            r.notes           AS notes,
            r.created_at      AS createdAt,
            r.late_cancellation AS lateCancellation,
            r.no_show_marked_at AS noShowMarkedAt,
            r.proposed_reservation_at AS proposedReservationAt,
            u.first_name      AS clientFirstName,
            u.last_name       AS clientLastName,
            u.phone           AS clientPhone,
            array_to_string(u.allergens, ',') AS clientAllergens,
            rest.name         AS restaurantName,
            rest.city         AS restaurantCity,
            rest.image        AS restaurantImage,
            rest.address          AS restaurantAddress,
            rest.latitude         AS restaurantLatitude,
            rest.longitude        AS restaurantLongitude,
            rest.google_place_id  AS restaurantGooglePlaceId,
            ms.name           AS mealServiceName,
            rz.name           AS zoneName,
            rt.table_number   AS tableNumber,
            (
                SELECT rsh.reason FROM reservation_status_histories rsh
                WHERE rsh.reservation_id = r.id AND rsh.new_status = 'refused'
                ORDER BY rsh.changed_at DESC LIMIT 1
            )                 AS refusalReason,
            (
                SELECT rsh.reason FROM reservation_status_histories rsh
                WHERE rsh.reservation_id = r.id AND rsh.new_status = 'cancelled'
                ORDER BY rsh.changed_at DESC LIMIT 1
            )                 AS cancellationReason
        FROM reservations r
        LEFT JOIN users               u    ON u.id    = r.client_id
        LEFT JOIN restaurants         rest ON rest.id = r.restaurant_id
        LEFT JOIN restaurant_tables   rt   ON rt.id   = r.table_id
        LEFT JOIN restaurant_zones    rz   ON rz.id   = rt.zone_id
        LEFT JOIN restaurant_services ms   ON ms.id   = r.service_id
        WHERE r.deleted_at IS NULL
          AND r.restaurant_id IN (:restaurantIds)
        ORDER BY r.reservation_at DESC
        LIMIT :limit
        """,
        nativeQuery = true)
    java.util.List<ReservationWithJoinsView> findEnrichedByRestaurantIds(
        @Param("restaurantIds") java.util.List<UUID> restaurantIds,
        @Param("limit") int limit);

    /**
     * Bug 31 — Agrégat (restaurantId, count) sur une période + statut optionnel,
     * pour la widget admin "Top Réservations · Par Ville".
     *
     * <p>Pattern volontairement minimal : retourne {@code List<Object[]>} (raw)
     * pour éviter de créer un DTO dédié à un simple KPI. Le service mappe vers
     * {@code Map<UUID, Long>} avant exposition — même style que
     * {@code AdminStatsFullService.reservationsByStatus} et autres agrégats
     * existants.
     *
     * <p>Native SQL avec {@code CAST(:param AS type) IS NULL OR ...} (même
     * convention que {@link #findAllWithJoins}) : sans le CAST explicite,
     * PostgreSQL n'arrive pas à inférer le type d'un placeholder bind à null
     * et renvoie "could not determine data type of parameter $X" (500).
     */
    @Query(value = """
        SELECT restaurant_id, COUNT(*)
        FROM reservations
        WHERE deleted_at IS NULL
          AND (CAST(:since  AS timestamp) IS NULL OR created_at >= CAST(:since  AS timestamp))
          AND (CAST(:status AS text)      IS NULL OR status      = CAST(:status AS text))
        GROUP BY restaurant_id
        ORDER BY COUNT(*) DESC
        """, nativeQuery = true)
    java.util.List<Object[]> countByRestaurantGrouped(
        @Param("since") Instant since,
        @Param("status") String status
    );

    /**
     * Gap #2 — destinataires « staff actif du restaurant » d'une nouvelle réservation
     * (le staff doit être notifié d'une demande à traiter). Staff actif = soft-delete
     * {@code deleted_at IS NULL} (la table {@code restaurant_staffs} n'a pas de colonne
     * status). On exclut le client demandeur par sécurité (un user staff pourrait aussi
     * être client de son propre resto). SQL natif (noms de tables) : la résolution se fait
     * côté module reservation et les UUID sont portés sur {@code ReservationCreatedEvent}
     * (frontière Modulith). Calque {@code AnnouncementRepository.findStaffRecipientIds}.
     */
    @Query(value = """
        SELECT rs.user_id
        FROM restaurant_staffs rs
        WHERE rs.restaurant_id = :restaurantId
          AND rs.deleted_at IS NULL
          AND rs.user_id <> :clientId
        """, nativeQuery = true)
    java.util.List<UUID> findStaffRecipientIdsForRestaurant(
        @Param("restaurantId") UUID restaurantId,
        @Param("clientId") UUID clientId
    );
}
