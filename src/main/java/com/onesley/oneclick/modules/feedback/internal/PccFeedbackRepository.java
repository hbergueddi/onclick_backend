package com.onesley.oneclick.modules.feedback.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository {@link PccFeedback} (PCC Lot 7) — finders dérivés (membre) + read-view native
 * owner-scope (inbox).
 *
 * <h3>Owner-scope cross-module (pattern P2.c)</h3>
 * <p>L'inbox owner restreint les avis à ceux dont le caller est owner actif du resto ciblé
 * ({@code target_restaurant_id}) OU aux avis généraux ({@code target_restaurant_id IS NULL}),
 * dans le tenant courant. Le filtre joint {@code restaurant_staffs}/{@code restaurants} —
 * tables des modules {@code restaurant} (CLOSED, non importables ici). On reste donc au niveau
 * SQL (noms de tables) en {@code nativeQuery}. Schéma Spring : {@code restaurant_staffs.role_code}
 * = 'owner' + soft-delete {@code deleted_at IS NULL} (pas de colonne {@code status='actif'} ni de
 * {@code t.slug='palmeraie'} comme le legacy — owner-scope GÉNÉRIQUE par tenant du caller).</p>
 */
@Repository
public interface PccFeedbackRepository extends JpaRepository<PccFeedback, UUID> {

    /** « Mes avis » du membre (caller = member_id), du plus récent au plus ancien. */
    List<PccFeedback> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    // ─── Read-view : inbox owner-scopée (avis ciblant SES restos + généraux) ─────

    /**
     * Avis visibles par un owner dans son tenant : ceux ciblant un resto dont {@code ownerUserId}
     * est owner actif ({@code restaurant_staffs.role_code='owner'} non supprimé) OU les avis
     * généraux ({@code target_restaurant_id IS NULL}). Restreint au {@code tenantId} (du caller),
     * du plus récent au plus ancien. Enrichi du nom du resto ciblé. Projection
     * {@link PccFeedbackOwnerView}.
     *
     * <p>Cohérent avec la RLS legacy {@code pcc_owners_read_scoped_feedbacks}, mais GÉNÉRIQUE :
     * scope = tenant du caller (passé en param), pas {@code slug='palmeraie'} en dur.</p>
     */
    @Query(value = """
        SELECT f.id                   AS id,
               f.member_id            AS memberId,
               f.tenant_id            AS tenantId,
               f.sentiment            AS sentiment,
               f.category             AS category,
               f.comment              AS comment,
               f.target_restaurant_id AS targetRestaurantId,
               tr.name                AS targetRestaurantName,
               f.reply_text           AS replyText,
               f.reply_by             AS replyBy,
               f.reply_at             AS replyAt,
               f.reply_read_by_member AS replyReadByMember,
               f.created_at           AS createdAt
        FROM pcc_feedbacks f
        LEFT JOIN restaurants tr ON tr.id = f.target_restaurant_id
        WHERE f.tenant_id = :tenantId
          AND (
                f.target_restaurant_id IS NULL
             OR EXISTS (
                  SELECT 1 FROM restaurant_staffs rs
                  WHERE rs.user_id = :ownerUserId
                    AND rs.restaurant_id = f.target_restaurant_id
                    AND rs.role_code = 'owner'
                    AND rs.deleted_at IS NULL
                )
          )
        ORDER BY f.created_at DESC
        """, nativeQuery = true)
    List<PccFeedbackOwnerView> findVisibleForOwner(@Param("ownerUserId") UUID ownerUserId,
                                                   @Param("tenantId") UUID tenantId);

    /**
     * true si {@code userId} est owner actif du resto {@code restaurantId} (read-view de contrôle
     * d'accès au {@code reply} owner-scope). Évite d'importer le module {@code restaurant}.
     */
    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM restaurant_staffs rs
            WHERE rs.user_id = :userId
              AND rs.restaurant_id = :restaurantId
              AND rs.role_code = 'owner'
              AND rs.deleted_at IS NULL
        )
        """, nativeQuery = true)
    boolean isActiveOwnerOf(@Param("userId") UUID userId, @Param("restaurantId") UUID restaurantId);

    // ─── Read-view : destinataires de la notif « nouvel avis » (owners + tenant-admins) ──

    /**
     * Résout les <b>destinataires</b> de la notification « nouvel avis membre » — port fidèle du
     * routage de l'Edge Function legacy {@code send-pcc-feedback} :
     * <ul>
     *   <li>les <b>tenant-admins</b> du tenant : users actifs de rôle {@code GROUP_ADMIN} ou
     *       {@code SUPERADMIN} rattachés au {@code tenantId} (Adil + équipe centrale — toujours
     *       notifiés, avis ciblé OU général) ;</li>
     *   <li>les <b>owners actifs du resto ciblé</b> ({@code restaurant_staffs.role_code='owner'}
     *       non supprimé) quand {@code targetRestaurantId} est non null (avis ciblé). Pour un avis
     *       général ({@code targetRestaurantId} null) cette branche ne ramène rien — seuls les
     *       tenant-admins sont notifiés, comme le legacy.</li>
     * </ul>
     *
     * <p>Le caller (auteur de l'avis) est exclu du résultat (jamais se notifier soi-même).
     * {@code DISTINCT} dédoublonne un user à la fois owner ET tenant-admin (équivalent du
     * {@code Set} legacy). Soft-deletes exclus partout.
     *
     * <p><b>Modulith</b> : la résolution joint {@code users}/{@code roles} (core.identity) et
     * {@code restaurant_staffs} (modules.restaurant) — non importables depuis {@code modules.feedback}
     * (CLOSED). On reste donc en {@code nativeQuery} (noms de tables), même invariant « 0 dépendance
     * business↔business » que {@link #findVisibleForOwner} / {@link #isActiveOwnerOf} (pattern P2.c).
     * Le résultat (liste d'UUID) est porté sur {@code FeedbackCreatedEvent} pour que
     * {@code core.notification} — qui n'a accès ni à identity ni à restaurant — n'ait qu'à itérer.
     */
    @Query(value = """
        SELECT DISTINCT recipient_id FROM (
            SELECT u.id AS recipient_id
            FROM users u
            JOIN roles r ON r.id = u.role_id
            WHERE u.tenant_id = :tenantId
              AND u.deleted_at IS NULL
              AND r.code IN ('GROUP_ADMIN', 'SUPERADMIN')
            UNION
            SELECT rs.user_id AS recipient_id
            FROM restaurant_staffs rs
            JOIN restaurants r ON r.id = rs.restaurant_id
              AND r.tenant_id = :tenantId
              AND r.deleted_at IS NULL
            WHERE :targetRestaurantId IS NOT NULL
              AND rs.restaurant_id = :targetRestaurantId
              AND rs.role_code = 'owner'
              AND rs.deleted_at IS NULL
        ) recipients
        WHERE recipient_id <> :authorId
        """, nativeQuery = true)
    List<UUID> findFeedbackRecipientIds(@Param("tenantId") UUID tenantId,
                                        @Param("targetRestaurantId") UUID targetRestaurantId,
                                        @Param("authorId") UUID authorId);

    /**
     * true si le resto {@code restaurantId} appartient au {@code tenantId} (non supprimé). Garde-fou
     * anti-spoof à la création d'un avis : un membre ne peut cibler qu'un resto de SON programme
     * (sinon la notif « nouvel avis » fuiterait vers l'owner d'un autre tenant — cf. branche owners
     * tenant-scopée de {@link #findFeedbackRecipientIds}). Read-view native (table {@code restaurants}
     * du module restaurant CLOSED, non importable) — même invariant que les autres finders du repo.
     */
    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM restaurants r
            WHERE r.id = :restaurantId
              AND r.tenant_id = :tenantId
              AND r.deleted_at IS NULL
        )
        """, nativeQuery = true)
    boolean restaurantBelongsToTenant(@Param("restaurantId") UUID restaurantId,
                                      @Param("tenantId") UUID tenantId);
}
