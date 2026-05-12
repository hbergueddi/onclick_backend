package com.onesley.oneclick.modules.reservation.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link ReservationGuest} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface ReservationGuestRepository extends JpaRepository<ReservationGuest, UUID>, JpaSpecificationExecutor<ReservationGuest> {
    java.util.List<ReservationGuest> findAllByReservationId(java.util.UUID reservationId);
    java.util.List<ReservationGuest> findAllByGuestUserId(java.util.UUID guestUserId);
}
