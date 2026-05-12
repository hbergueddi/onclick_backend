package com.onesley.oneclick.modules.resource_booking.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link ResourceBooking} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface ResourceBookingRepository extends JpaRepository<ResourceBooking, UUID>, JpaSpecificationExecutor<ResourceBooking> {
    java.util.List<ResourceBooking> findAllByResourceId(java.util.UUID resourceId);
    java.util.List<ResourceBooking> findAllByOrganizerId(java.util.UUID organizerId);
    java.util.List<ResourceBooking> findAllByPricingId(java.util.UUID pricingId);
}
