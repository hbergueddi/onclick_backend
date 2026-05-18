package com.onesley.oneclick.modules.reservation.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
            u.first_name      AS clientFirstName,
            u.last_name       AS clientLastName,
            u.phone           AS clientPhone,
            rest.name         AS restaurantName,
            rest.city         AS restaurantCity,
            rest.image        AS restaurantImage,
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
        ORDER BY r.reservation_at DESC
        """,
        countQuery = """
        SELECT COUNT(*)
        FROM reservations r
        WHERE r.deleted_at IS NULL
          AND (CAST(:clientId     AS uuid) IS NULL OR r.client_id     = CAST(:clientId     AS uuid))
          AND (CAST(:restaurantId AS uuid) IS NULL OR r.restaurant_id = CAST(:restaurantId AS uuid))
          AND (:status IS NULL OR r.status = :status)
        """,
        nativeQuery = true)
    Page<ReservationWithJoinsView> findAllWithJoins(
        @Param("clientId") UUID clientId,
        @Param("restaurantId") UUID restaurantId,
        @Param("status") String status,
        Pageable pageable
    );
}
