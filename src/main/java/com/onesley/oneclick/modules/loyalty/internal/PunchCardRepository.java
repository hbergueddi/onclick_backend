package com.onesley.oneclick.modules.loyalty.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository {@link PunchCard} — 1 carte par (tenant, client, activité).
 *
 * <p>Pas de soft delete sur les cartes (une carte de fidélité vit). Les finders
 * sont alignés sur la contrainte UNIQUE {@code (tenant_id, client_id, activity)}.</p>
 */
@Repository
public interface PunchCardRepository extends JpaRepository<PunchCard, UUID> {

    /** Toutes les cartes d'un client dans un tenant (lecture « mes cartes »). */
    List<PunchCard> findByTenantIdAndClientIdOrderByActivityAsc(UUID tenantId, UUID clientId);

    /** La carte unique (tenant, client, activité) — clé d'upsert du punch. */
    Optional<PunchCard> findByTenantIdAndClientIdAndActivity(UUID tenantId, UUID clientId, String activity);

    /** Toutes les cartes d'un tenant (export staff/admin — Gap #3). */
    List<PunchCard> findByTenantIdOrderByActivityAscClientIdAsc(UUID tenantId);
}
