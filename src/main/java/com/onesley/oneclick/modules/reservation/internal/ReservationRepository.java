package com.onesley.oneclick.modules.reservation.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
}
