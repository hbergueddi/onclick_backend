package com.onesley.oneclick.modules.promotion.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Repository {@link Offer} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 *
 * <h3>Périmètre tenant (fuite de périmètre)</h3>
 * <p>L'entité {@link Offer} ne mappe pas de propriété {@code tenantId} (le tenant se déduit via
 * {@code offers.restaurant_id → restaurants.tenant_id} — {@code restaurants.tenant_id} étant la
 * source de vérité). Pour fermer la fuite (un client oneclick non-membre ne doit voir que les offres
 * des restos du tenant public ∪ ses memberships), on ajoute des requêtes <b>natives</b> qui JOINent
 * {@code restaurants}
 * (référence SQL d'une table d'un autre module — autorisé, jamais l'entité ; pattern
 * {@code modules.analytics.internal.TenantOffersService} + {@code AnnouncementRepository}).
 * Le bypass cross-tenant (SUPERADMIN, {@code visibleTenantIdsOrNull()==null}) reste géré
 * côté service : il appelle alors les finders Specification legacy non scopés.
 */
@Repository
public interface OfferRepository extends JpaRepository<Offer, UUID>, JpaSpecificationExecutor<Offer> {
    List<Offer> findAllByRestaurantId(UUID restaurantId);

    /**
     * Liste paginée des offres <b>scopée au périmètre tenant</b> du caller — JOIN
     * {@code restaurants} pour filtrer {@code r.tenant_id IN (:tenantIds)}. Vivantes
     * ({@code deleted_at IS NULL}). Filtres optionnels :
     * <ul>
     *   <li>{@code restaurantId} (UUID nullable) — garde {@code CAST(:restaurantId AS uuid) IS NULL OR ...}
     *       (piège Postgres « 500 native SQL CAST » sur param UUID nullable) ;</li>
     *   <li>{@code activeOnly} (boolean) — {@code enabled = true AND starts_at <= now AND expires_at > now}.</li>
     * </ul>
     * Tri {@code starts_at DESC} (identique au finder Specification legacy).
     */
    @Query(value = """
        SELECT o.*
        FROM offers o
        JOIN restaurants r ON r.id = o.restaurant_id
        WHERE o.deleted_at IS NULL
          AND r.tenant_id IN (:tenantIds)
          AND (CAST(:restaurantId AS uuid) IS NULL OR o.restaurant_id = CAST(:restaurantId AS uuid))
          AND (
                :activeOnly = FALSE
             OR (o.enabled = TRUE AND o.starts_at <= :now AND o.expires_at > :now)
          )
        ORDER BY o.starts_at DESC
        """,
        countQuery = """
        SELECT COUNT(*)
        FROM offers o
        JOIN restaurants r ON r.id = o.restaurant_id
        WHERE o.deleted_at IS NULL
          AND r.tenant_id IN (:tenantIds)
          AND (CAST(:restaurantId AS uuid) IS NULL OR o.restaurant_id = CAST(:restaurantId AS uuid))
          AND (
                :activeOnly = FALSE
             OR (o.enabled = TRUE AND o.starts_at <= :now AND o.expires_at > :now)
          )
        """,
        nativeQuery = true)
    Page<Offer> findAllScoped(@Param("tenantIds") Collection<UUID> tenantIds,
                              @Param("restaurantId") UUID restaurantId,
                              @Param("activeOnly") boolean activeOnly,
                              @Param("now") Instant now,
                              Pageable pageable);

    /**
     * Tenant (via le restaurant) d'une offre — pour le contrôle de périmètre par-id
     * ({@code findById} / {@code recordImpression}). {@code null} si l'offre / le restaurant
     * n'existe pas (le service traite alors comme NotFound).
     */
    @Query(value = """
        SELECT r.tenant_id
        FROM offers o
        JOIN restaurants r ON r.id = o.restaurant_id
        WHERE o.id = :offerId
        """, nativeQuery = true)
    UUID findTenantIdOfOffer(@Param("offerId") UUID offerId);

    /**
     * Ids des restaurants <b>visibles</b> du caller (appartenant à {@code :tenantIds}) — utilisé
     * pour contraindre la recherche dynamique ({@code POST /search}) au périmètre, les offres
     * n'ayant pas de {@code tenant_id} filtrable côté Specification JPA.
     */
    @Query(value = """
        SELECT r.id
        FROM restaurants r
        WHERE r.tenant_id IN (:tenantIds)
        """, nativeQuery = true)
    List<UUID> findRestaurantIdsInTenants(@Param("tenantIds") Collection<UUID> tenantIds);

    /**
     * Lot B11 — IDs des staff ACTIFS d'un restaurant (destinataires de la notif « offre expirée »).
     * Staff actif = {@code restaurant_staffs.deleted_at IS NULL} (pas de colonne status). SQL natif
     * (noms de tables) : la résolution reste côté module {@code promotion} et les UUID sont portés sur
     * {@code OfferExpiredEvent} (frontière Modulith). Calque
     * {@code ResourceBookingRepository.findStaffRecipientIdsForTenant}.
     */
    @Query(value = """
        SELECT rs.user_id
        FROM restaurant_staffs rs
        WHERE rs.restaurant_id = :restaurantId
          AND rs.deleted_at IS NULL
        """, nativeQuery = true)
    List<UUID> findStaffRecipientIdsForRestaurant(@Param("restaurantId") UUID restaurantId);
}
