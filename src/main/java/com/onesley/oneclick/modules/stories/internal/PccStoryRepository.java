package com.onesley.oneclick.modules.stories.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository {@link PccStory} (PCC) — lectures scopées (membre/staff) + read-view native de
 * visibilité cross-module (staff actif du tenant).
 *
 * <h3>Read-view cross-module (pattern P2.c — Modulith CLOSED)</h3>
 * <p>L'écriture (create/update/delete) est réservée au <b>staff actif du tenant</b> : l'utilisateur
 * a au moins une affectation {@code restaurant_staffs} (soft-delete {@code deleted_at IS NULL}) sur
 * un resto du tenant ({@code restaurants.tenant_id}). Ces tables vivent dans {@code modules.restaurant}
 * (CLOSED) — non importables ici. On reste donc au niveau SQL (noms de tables) en {@code nativeQuery},
 * comme {@code AnnouncementRepository.isActiveStaffOfTenant} / {@code PccFeedbackRepository}. GÉNÉRIQUE :
 * scope = {@code tenantId} (du caller) passé en paramètre, jamais {@code slug='palmeraie'} en dur.</p>
 */
@Repository
public interface PccStoryRepository extends JpaRepository<PccStory, UUID> {

    // ─── Contrôle d'accès (read-view native) ─────────────────────────────────────

    /**
     * true si {@code userId} est <b>staff actif</b> d'au moins un resto du tenant
     * ({@code restaurant_staffs} non supprimé {@code JOIN restaurants} sur {@code tenant_id}).
     * Base de l'écriture (create/update/delete) + de la lecture étendue staff (programmées/expirées).
     */
    @Query(value = """
        SELECT EXISTS (
            SELECT 1
            FROM restaurant_staffs rs
            JOIN restaurants r ON r.id = rs.restaurant_id
            WHERE rs.user_id = :userId
              AND rs.deleted_at IS NULL
              AND r.tenant_id = :tenantId
        )
        """, nativeQuery = true)
    boolean isActiveStaffOfTenant(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);

    // ─── Lectures scopées ────────────────────────────────────────────────────────

    /**
     * Stories <b>visibles des membres</b> d'un tenant : vivantes (non soft-deletées), publiées
     * ({@code publish_at <= now}) et non expirées ({@code expires_at IS NULL OR expires_at > now}),
     * du tenant donné. Tri : {@code sort_order} asc puis {@code publish_at} asc (carrousel
     * chronologique, cohérent avec le legacy {@code usePccStories} qui ordonne par event_date asc).
     */
    @Query(value = """
        SELECT s.*
        FROM pcc_stories s
        WHERE s.tenant_id = :tenantId
          AND s.deleted_at IS NULL
          AND s.publish_at <= :now
          AND (s.expires_at IS NULL OR s.expires_at > :now)
        ORDER BY s.sort_order ASC, s.publish_at ASC
        """, nativeQuery = true)
    List<PccStory> findActiveForTenant(@Param("tenantId") UUID tenantId, @Param("now") Instant now);

    /**
     * Stories visibles d'un <b>staff/admin</b> (gestion) : TOUT le tenant sauf les soft-deletées
     * (inclut les programmées {@code publish_at} futur ET les expirées — pour pilotage). Tri par
     * ordre puis date de publication décroissante (les plus récentes/à venir en tête à ordre égal).
     */
    @Query(value = """
        SELECT s.*
        FROM pcc_stories s
        WHERE s.tenant_id = :tenantId
          AND s.deleted_at IS NULL
        ORDER BY s.sort_order ASC, s.publish_at DESC
        """, nativeQuery = true)
    List<PccStory> findAllForStaff(@Param("tenantId") UUID tenantId);
}
