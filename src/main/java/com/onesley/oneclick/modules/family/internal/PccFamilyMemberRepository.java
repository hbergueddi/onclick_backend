package com.onesley.oneclick.modules.family.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository {@link PccFamilyMember} (PCC Lot 5) — finders dérivés + read-views SQL natives
 * pour les points fidélité d'un proche.
 *
 * <h3>Read-views cross-module (pattern P2.c)</h3>
 * <p>Le total de points restants et l'historique de points d'un proche sont des données du
 * module {@code loyalty} (tables {@code loyalty_accounts}/{@code loyalty_transactions}) joint
 * à {@code restaurants} (module {@code restaurant}). Le module {@code family} étant CLOSED ne
 * peut pas importer ces entités → on lit au niveau SQL (noms de tables) en {@code nativeQuery}.
 * Aucune dépendance business↔business, invariant Modulith respecté.</p>
 */
@Repository
public interface PccFamilyMemberRepository extends JpaRepository<PccFamilyMember, UUID> {

    /** Nombre de proches déjà ajoutés par le caller (A) — contrôle de la limite 10. */
    long countByMemberId(UUID memberId);

    /** Liste « ma famille » du caller (A), du plus récent au plus ancien. */
    List<PccFamilyMember> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    /** Lien (A→B) s'il existe — idempotence de l'ajout + contrôle d'accès à l'historique. */
    Optional<PccFamilyMember> findByMemberIdAndRelatedMemberId(UUID memberId, UUID relatedMemberId);

    // ─── Read-view : total points restants d'un proche, dans un tenant ───────────

    /**
     * Somme des soldes de fidélité ({@code loyalty_accounts.balance}) d'un client, restreinte
     * aux restaurants du tenant donné. {@code COALESCE → 0} si le client n'a aucun compte dans
     * ce tenant. C'est le « total points restants » affiché en face de chaque proche.
     */
    @Query(value = """
        SELECT COALESCE(SUM(la.balance), 0)
        FROM loyalty_accounts la
        JOIN restaurants r ON r.id = la.restaurant_id
        WHERE la.client_id = :clientId
          AND r.tenant_id = :tenantId
        """, nativeQuery = true)
    long sumRemainingPointsByClientInTenant(@Param("clientId") UUID clientId, @Param("tenantId") UUID tenantId);

    // ─── Read-view : historique de points d'un proche, dans un tenant ────────────

    /**
     * Historique complet des mouvements de points d'un client, restreint aux restaurants du
     * tenant donné (du plus récent au plus ancien). {@code remainingPoints} = solde courant du
     * compte (la, par restaurant) — analogue du {@code remaining_points} FIFO legacy non tenu
     * dans le modèle comptable Spring. Projection {@link PccFamilyPointsHistoryView}.
     */
    @Query(value = """
        SELECT lt.id            AS id,
               la.restaurant_id AS restaurantId,
               r.name           AS restaurantName,
               lt.points        AS points,
               lt.amount        AS amountTtc,
               lt.reason        AS reason,
               lt.created_at    AS earnedAt,
               la.balance       AS remainingPoints,
               lt.expires_at    AS expiresAt
        FROM loyalty_transactions lt
        JOIN loyalty_accounts la ON la.id = lt.account_id
        JOIN restaurants r ON r.id = la.restaurant_id
        WHERE la.client_id = :clientId
          AND r.tenant_id = :tenantId
        ORDER BY lt.created_at DESC
        """, nativeQuery = true)
    List<PccFamilyPointsHistoryView> pointsHistoryByClientInTenant(@Param("clientId") UUID clientId, @Param("tenantId") UUID tenantId);
}
