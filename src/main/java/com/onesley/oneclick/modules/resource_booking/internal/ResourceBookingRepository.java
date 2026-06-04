package com.onesley.oneclick.modules.resource_booking.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    /**
     * Bookings actifs (non soft-deleted) d'une ressource qui chevauchent une plage
     * temporelle [from, to) et dont le statut occupe le créneau ({@code statuses}).
     *
     * <p>Chevauchement = {@code startAt < to AND endAt > from} (les annulées / no_show
     * sont exclues via {@code statuses} → elles libèrent le créneau). Tri par {@code startAt}
     * pour un calendrier ordonné. Utilisé par {@code busy-slots} (projection sans PII).</p>
     */
    @Query("""
        SELECT b FROM ResourceBooking b
         WHERE b.resourceId = :resourceId
           AND b.deletedAt IS NULL
           AND b.status IN :statuses
           AND b.startAt < :to
           AND b.endAt > :from
         ORDER BY b.startAt ASC
        """)
    List<ResourceBooking> findActiveInRange(
        @Param("resourceId") UUID resourceId,
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("statuses") Collection<String> statuses);
}
