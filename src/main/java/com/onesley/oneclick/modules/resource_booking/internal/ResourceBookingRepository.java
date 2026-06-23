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

    /**
     * Gap #3 — destinataires « staff actif du tenant » d'un nouveau booking PCC. Staff actif =
     * {@code restaurant_staffs.deleted_at IS NULL} (pas de colonne status). On exclut l'organisateur
     * (qui pourrait être staff de son propre club). SQL natif (noms de tables) : résolution côté
     * module {@code resource_booking}, UUID portés sur {@code ResourceBookingCreatedEvent}
     * (frontière Modulith). Calque {@code AnnouncementRepository.findStaffRecipientIds}.
     */
    @Query(value = """
        SELECT DISTINCT rs.user_id
        FROM restaurant_staffs rs
        JOIN restaurants r ON r.id = rs.restaurant_id
        WHERE r.tenant_id = :tenantId
          AND rs.deleted_at IS NULL
          AND rs.user_id <> :organizerId
        """, nativeQuery = true)
    List<UUID> findStaffRecipientIdsForTenant(
        @Param("tenantId") UUID tenantId,
        @Param("organizerId") UUID organizerId);

    /**
     * P1.3 — nombre de bookings <b>actifs</b> (non soft-deleted) rattachés à une ressource. Utilisé
     * pour refuser (409 Conflict) la suppression d'une ressource ayant des réservations vivantes :
     * un booking soft-deleted (annulé) ne compte pas → la ressource redevient supprimable une fois
     * ses réservations purgées. (« Désactivez la ressource plutôt que de la supprimer. »)
     */
    @Query("SELECT COUNT(b) FROM ResourceBooking b WHERE b.resourceId = :resourceId AND b.deletedAt IS NULL")
    long countActiveByResourceId(@Param("resourceId") UUID resourceId);

    /**
     * P1.4 — agrégation no-show <b>par organisateur</b> sur les bookings (non soft-deleted) des
     * ressources d'un tenant, dans la fenêtre {@code [from, to)} (sur {@code startAt}).
     *
     * <p>Projection en une seule requête (anti-N+1) : {@code (organizerId, total, honored, noShows,
     * cancelled, lastNoShowAt)}. Le scope tenant passe par un JOIN sur {@code resource.tenantId} ;
     * le tenant vient TOUJOURS du contexte sécurité (jamais d'un paramètre client). Le calcul du
     * taux et le tri/enrichissement (nom d'affichage) sont faits côté service. {@code lastNoShowAt}
     * = MAX({@code startAt}) parmi les bookings {@code no_show} de l'organisateur (NULL sinon).</p>
     */
    @Query("""
        SELECT b.organizerId,
               COUNT(b),
               SUM(CASE WHEN b.status = 'completed' THEN 1L ELSE 0L END),
               SUM(CASE WHEN b.status = 'no_show'   THEN 1L ELSE 0L END),
               SUM(CASE WHEN b.status = 'cancelled' THEN 1L ELSE 0L END),
               MAX(CASE WHEN b.status = 'no_show' THEN b.startAt ELSE NULL END)
          FROM ResourceBooking b
         WHERE b.resource.tenantId = :tenantId
           AND b.deletedAt IS NULL
           AND b.startAt >= :from
           AND b.startAt <  :to
         GROUP BY b.organizerId
        """)
    List<Object[]> aggregateNoShowStatsByOrganizer(
        @Param("tenantId") UUID tenantId,
        @Param("from") Instant from,
        @Param("to") Instant to);
}
