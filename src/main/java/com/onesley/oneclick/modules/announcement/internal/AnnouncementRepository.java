package com.onesley.oneclick.modules.announcement.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository {@link Announcement} (Lot 8) — finders dérivés + read-views natives de visibilité
 * cross-module (staff actif / tenant-admin) + désépinglage.
 *
 * <h3>Read-views cross-module (pattern P2.c — Modulith CLOSED)</h3>
 * <p>La visibilité d'une annonce dépend de deux notions qui vivent dans d'autres modules :
 * <ul>
 *   <li><b>« staff actif du tenant »</b> : l'utilisateur a au moins une affectation
 *       {@code restaurant_staffs} (soft-delete {@code deleted_at IS NULL}) sur un resto du tenant
 *       ({@code restaurants.tenant_id}). Ces tables vivent dans {@code modules.restaurant}
 *       (CLOSED) — non importables ici. On reste donc au niveau SQL (noms de tables) en
 *       {@code nativeQuery}, comme {@code PccFeedbackRepository.findVisibleForOwner}.</li>
 *   <li><b>« tenant-admin »</b> : utilisateur de rôle {@code GROUP_ADMIN} ou {@code SUPERADMIN}
 *       rattaché au tenant ({@code users.tenant_id} + {@code roles}). En Spring il n'existe PAS de
 *       table {@code tenant_admins} (legacy) — l'identité admin est portée par le rôle. Ces tables
 *       vivent dans {@code core.identity} (OPEN) mais on garde la requête en natif pour homogénéité
 *       avec le reste (et éviter une dépendance entité).</li>
 * </ul>
 * GÉNÉRIQUE : scope = {@code tenantId} (du caller) passé en paramètre, jamais {@code slug='palmeraie'}
 * en dur → réutilisable par tout tenant whitelabel.</p>
 */
@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    // ─── Contrôles d'accès (read-views natives) ──────────────────────────────────

    /**
     * true si {@code userId} est <b>staff actif</b> d'au moins un resto du tenant
     * ({@code restaurant_staffs} non supprimé {@code JOIN restaurants} sur {@code tenant_id}).
     * Base de la lecture staff (annonces publiées) + de la visibilité de la bannière.
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

    /**
     * true si {@code userId} est <b>tenant-admin</b> du tenant : user actif rattaché au
     * {@code tenantId} ({@code users.tenant_id}) avec un rôle de gestion
     * {@code RESTAURATEUR}/{@code GROUP_ADMIN}/{@code SUPERADMIN}. C'est la population « écriture »
     * (create/update/archive/delete) + lecture étendue (scheduled + archived).
     *
     * <p><b>Pourquoi RESTAURATEUR ?</b> Dans le modèle Spring, le « tenant-admin » au sens legacy
     * (Adil pour PCC) est porté par les <b>owners</b> du tenant = rôle {@code RESTAURATEUR} scopé au
     * tenant ({@code users.tenant_id}). Il n'existe pas systématiquement de {@code GROUP_ADMIN} par
     * tenant. La frontière reste générique (scope = {@code tenantId} du caller) et cohérente avec
     * les grants V70 ({@code CREATE/UPDATE/DELETE:ANNOUNCEMENTS} → RESTAURATEUR/GROUP_ADMIN/SUPERADMIN).
     * Le {@code STAFF} est EXCLU (lecture seule — pas un tenant-admin).</p>
     *
     * <p>Le SUPERADMIN « global » (sans {@code tenant_id}) n'est PAS capté ici par construction (il
     * n'a pas de tenant) — le service traite ce cas via {@code SecurityHelper.isAdmin()} (bypass
     * admin global). Cette read-view ne qualifie qu'un admin RATTACHÉ au tenant.</p>
     */
    @Query(value = """
        SELECT EXISTS (
            SELECT 1
            FROM users u
            JOIN roles r ON r.id = u.role_id
            WHERE u.id = :userId
              AND u.tenant_id = :tenantId
              AND u.deleted_at IS NULL
              AND r.code IN ('RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
        )
        """, nativeQuery = true)
    boolean isTenantAdmin(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);

    // ─── Lectures scopées ────────────────────────────────────────────────────────

    /**
     * Annonces <b>visibles du staff</b> d'un tenant : publiées ({@code publish_at <= now}), vivantes
     * ({@code archived_at IS NULL AND deleted_at IS NULL}), du tenant donné. Tri : épinglées
     * d'abord, puis priorité (urgent avant permanent), puis plus récentes.
     */
    @Query(value = """
        SELECT a.*
        FROM tenant_announcements a
        WHERE a.tenant_id = :tenantId
          AND a.deleted_at IS NULL
          AND a.archived_at IS NULL
          AND a.publish_at <= :now
        ORDER BY a.is_pinned DESC,
                 (a.priority = 'urgent') DESC,
                 a.created_at DESC
        """, nativeQuery = true)
    List<Announcement> findActiveForTenant(@Param("tenantId") UUID tenantId, @Param("now") Instant now);

    /**
     * Annonces visibles d'un <b>tenant-admin</b> : TOUT le tenant sauf les soft-deletées (inclut
     * les programmées {@code publish_at} futur ET les archivées — pour gestion). Même tri que la
     * vue staff.
     */
    @Query(value = """
        SELECT a.*
        FROM tenant_announcements a
        WHERE a.tenant_id = :tenantId
          AND a.deleted_at IS NULL
        ORDER BY a.is_pinned DESC,
                 (a.priority = 'urgent') DESC,
                 a.created_at DESC
        """, nativeQuery = true)
    List<Announcement> findAllForAdmin(@Param("tenantId") UUID tenantId);

    // ─── Invariant « max 1 épinglée par priorité par tenant » ────────────────────

    /**
     * Désépingle toutes les annonces VIVANTES (non archivées/supprimées) du tenant qui partagent la
     * {@code priority}, SAUF {@code keepId} (celle qu'on vient d'épingler). Appelé par le service
     * après avoir épinglé une annonce, pour garantir au plus 1 épinglée par priorité/tenant.
     * Retourne le nombre de lignes désépinglées.
     */
    @Modifying
    @Query(value = """
        UPDATE tenant_announcements
        SET is_pinned = false, updated_at = now()
        WHERE tenant_id = :tenantId
          AND priority = :priority
          AND id <> :keepId
          AND is_pinned = true
          AND archived_at IS NULL
          AND deleted_at IS NULL
        """, nativeQuery = true)
    int unpinOthers(@Param("tenantId") UUID tenantId,
                    @Param("priority") String priority,
                    @Param("keepId") UUID keepId);

    // ─── Read-view : destinataires de la notif « nouvelle annonce » ──────────────

    /**
     * Résout les <b>destinataires</b> de la notification « nouvelle annonce publiée » — la
     * population à prévenir = le <b>staff actif du tenant</b> (au moins une affectation
     * {@code restaurant_staffs} non supprimée sur un resto du tenant). L'auteur ({@code authorId})
     * est exclu (jamais se notifier soi-même). {@code DISTINCT} dédoublonne un user staffé sur
     * plusieurs restos du tenant.
     *
     * <p><b>Modulith</b> : la résolution joint {@code restaurant_staffs}/{@code restaurants}
     * (modules.restaurant) — non importables depuis {@code modules.announcement} (CLOSED). On reste
     * donc en {@code nativeQuery} (noms de tables), même invariant « 0 dépendance business↔business »
     * que {@link #isActiveStaffOfTenant} (pattern P2.c). Le résultat (liste d'UUID) est porté sur
     * {@code AnnouncementPublishedEvent} pour que {@code core.notification} — qui n'a accès ni à
     * identity ni à restaurant — n'ait qu'à itérer.</p>
     *
     * <p>NB : seules les annonces déjà publiées ({@code publish_at <= now}) déclenchent la notif au
     * staff. Pour une annonce programmée (futur), le service ne publie pas l'event à la création (le
     * push différé est V1 hors scope — cron) ; on n'a donc pas à filtrer ici sur publish_at.</p>
     */
    @Query(value = """
        SELECT DISTINCT rs.user_id
        FROM restaurant_staffs rs
        JOIN restaurants r ON r.id = rs.restaurant_id
        WHERE r.tenant_id = :tenantId
          AND rs.deleted_at IS NULL
          AND rs.user_id <> :authorId
        """, nativeQuery = true)
    List<UUID> findStaffRecipientIds(@Param("tenantId") UUID tenantId,
                                     @Param("authorId") UUID authorId);
}
