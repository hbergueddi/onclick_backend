package com.onesley.oneclick.modules.reservation.internal;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
