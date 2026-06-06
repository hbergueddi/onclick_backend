package com.onesley.oneclick.modules.seminar.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository {@link SeminarRequest} (PCC) — finders dérivés (membre / inbox tenant) + read-view
 * native des destinataires de notification.
 *
 * <h3>Inbox commercial tenant-wide</h3>
 * <p>Contrairement à {@code modules.feedback} (owner-scope par resto), l'inbox séminaire est
 * <b>tenant-wide</b> : tout staff/admin actif du tenant voit toutes les demandes du tenant (port
 * de la RLS legacy « organizer = self OR admin OR active staff du tenant »). Le filtre se fait donc
 * sur {@code tenant_id} seul (finder dérivé JPA), sans join {@code restaurant_staffs} — plus simple.</p>
 */
@Repository
public interface SeminarRequestRepository extends JpaRepository<SeminarRequest, UUID> {

    /** « Mes demandes » du membre (caller = organizer_id), du plus récent au plus ancien. */
    List<SeminarRequest> findByOrganizerIdOrderByCreatedAtDesc(UUID organizerId);

    /** Inbox commercial : toutes les demandes du tenant, du plus récent au plus ancien. */
    List<SeminarRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    // ─── Read-view : destinataires de la notif « nouvelle demande » (staff/admins du tenant) ──

    /**
     * Résout les <b>destinataires</b> de la notification « nouvelle demande de séminaire » : les
     * users actifs du tenant dont le rôle est opérationnel/commercial
     * ({@code STAFF | RESTAURATEUR | GROUP_ADMIN | SUPERADMIN}). Le caller (auteur) est exclu.
     *
     * <p><b>Modulith</b> : la résolution joint {@code users}/{@code roles} (core.identity) — non
     * importables depuis {@code modules.seminar} (CLOSED). On reste en {@code nativeQuery} (noms de
     * tables), même invariant « 0 dépendance business↔business » que {@code modules.feedback}
     * (pattern P2.c). Le résultat (liste d'UUID) est porté sur {@code SeminarRequestedEvent} pour que
     * {@code core.notification} n'ait qu'à itérer. Soft-deletes exclus.
     */
    @Query(value = """
        SELECT u.id
        FROM users u
        JOIN roles r ON r.id = u.role_id
        WHERE u.tenant_id = :tenantId
          AND u.deleted_at IS NULL
          AND r.code IN ('STAFF', 'RESTAURATEUR', 'GROUP_ADMIN', 'SUPERADMIN')
          AND u.id <> :authorId
        """, nativeQuery = true)
    List<UUID> findSeminarRecipientIds(@Param("tenantId") UUID tenantId,
                                       @Param("authorId") UUID authorId);
}
