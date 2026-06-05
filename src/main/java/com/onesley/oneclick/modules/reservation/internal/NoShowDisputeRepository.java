package com.onesley.oneclick.modules.reservation.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository {@link NoShowDispute} — contestations de no_show (Feature #3).
 */
@Repository
public interface NoShowDisputeRepository extends JpaRepository<NoShowDispute, UUID> {

    /** Toutes les contestations d'une réservation (récentes d'abord) — éligibilité + listing. */
    List<NoShowDispute> findByReservationIdOrderByCreatedAtDesc(UUID reservationId);

    /** Contestations d'un lot de restaurants (dashboard resto/support scoping ABAC). */
    List<NoShowDispute> findByRestaurantIdInOrderByCreatedAtDesc(List<UUID> restaurantIds);

    /** Toutes les contestations (dashboard admin/support — récentes d'abord). */
    List<NoShowDispute> findAllByOrderByCreatedAtDesc();

    // ─── Read-view : contexte d'affichage des résas contestées (nom resto + horodatage) ──

    /**
     * Contexte d'affichage d'un <b>lot</b> de réservations contestées (anti N+1) : pour chaque
     * {@code reservationId}, le nom du restaurant ({@code restaurants.name}) et l'horodatage de
     * la résa ({@code reservations.reservation_at}). Une seule requête pour toute une page de
     * disputes → le service construit ensuite une {@code Map<reservationId, view>}.
     *
     * <p>Soft-deletes exclus ({@code deleted_at IS NULL}) côté résa ET resto : une résa / un
     * restaurant supprimé ne ramène pas de contexte (le DTO portera des nulls — best-effort).
     * {@code restaurant_id} de la dispute = {@code restaurant_id} de la résa par construction
     * ({@code NoShowDisputeService.create}), donc le JOIN sur {@code res.restaurant_id} est
     * canonique.</p>
     *
     * <p><b>Modulith (P2.c)</b> : le nom du resto vit dans {@code restaurants}
     * ({@code modules.restaurant.internal}), non importable depuis {@code modules.reservation}
     * (CLOSED). On reste au niveau SQL (noms de tables) en {@code nativeQuery} — même invariant
     * « 0 dépendance business↔business » que {@code PccFeedbackRepository.findVisibleForOwner}.
     * Projection {@link DisputeReservationView}. {@code reservations} appartient bien à CE module
     * (pas de franchissement de frontière pour cette table), seul {@code restaurants} est externe.</p>
     */
    @Query(value = """
        SELECT res.id              AS reservationId,
               r.name              AS restaurantName,
               res.reservation_at  AS reservationDateTime
        FROM reservations res
        JOIN restaurants r ON r.id = res.restaurant_id AND r.deleted_at IS NULL
        WHERE res.id IN (:reservationIds)
          AND res.deleted_at IS NULL
        """, nativeQuery = true)
    List<DisputeReservationView> findReservationContextByIds(@Param("reservationIds") List<UUID> reservationIds);
}
