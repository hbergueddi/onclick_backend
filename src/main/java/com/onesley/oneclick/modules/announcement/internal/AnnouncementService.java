package com.onesley.oneclick.modules.announcement.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi.UserName;
import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.announcement.api.AnnouncementDtos.AnnouncementDto;
import com.onesley.oneclick.modules.announcement.api.AnnouncementDtos.CreateAnnouncementDto;
import com.onesley.oneclick.modules.announcement.api.AnnouncementDtos.UpdateAnnouncementDto;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.AnnouncementPublishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Logique « Annonces tenant » (Lot 8) — comm B2B staff descendante (tenant-admin → owners/staff),
 * port fidèle du legacy Supabase ({@code tenant_announcements} + Edge Functions de gestion) vers un
 * service Spring avec ABAC. GÉNÉRIQUE : tout est scopé au tenant DU CALLER (résolu via
 * {@code UserDirectoryApi}), jamais {@code slug='palmeraie'} en dur.
 *
 * <h3>Mapping triggers SQL legacy → SERVICE (directive senior : 0 trigger)</h3>
 * <ul>
 *   <li>{@code trg_announcement_max_pinned} (AFTER INSERT/UPDATE → unpin autres même priorité) →
 *       {@link #enforceMaxPinned} : après save d'une annonce épinglée, on désépingle les autres de
 *       la même priorité/tenant via {@code AnnouncementRepository.unpinOthers}.</li>
 *   <li>{@code trg_announcement_body_version} (BEFORE UPDATE OF body → bump body_version + DELETE
 *       reads) → {@link #update} : {@code Announcement.applyEdit} bump {@code bodyVersion} si le body
 *       change ; le service purge alors les acquittements ({@code AnnouncementReadRepository
 *       .deleteByAnnouncementId}) → ré-acquittement requis.</li>
 *   <li>{@code trg_announcement_updated_at} → géré par l'auditing JPA ({@code @LastModifiedDate} de
 *       {@code TimestampedEntity}), pas besoin de trigger.</li>
 * </ul>
 *
 * <h3>ABAC</h3>
 * <ul>
 *   <li><b>Lecture</b> ({@link #listForMe}) : tenant du caller. tenant-admin (read-view
 *       {@code isTenantAdmin} OU admin global {@code SecurityHelper.isAdmin()}) → TOUT le tenant
 *       (incl. programmées + archivées). Sinon staff actif du tenant ({@code isActiveStaffOfTenant})
 *       → annonces publiées vivantes. Un non-staff → liste vide (défense en profondeur ; l'endpoint
 *       exige VIEW:ANNOUNCEMENTS que seul le staff possède).</li>
 *   <li><b>Écriture</b> ({@link #create}/{@link #update}/{@link #archive}/{@link #softDelete}) :
 *       tenant-admin du tenant OU admin global uniquement (403 sinon). Toute mutation est restreinte
 *       à une annonce DU tenant du caller (isolation cross-tenant).</li>
 *   <li><b>mark-read</b> ({@link #markRead}) : self (le caller acquitte pour lui-même). Doit être
 *       staff/admin du tenant de l'annonce (sinon 403 — un user hors tenant n'acquitte rien).</li>
 * </ul>
 *
 * <h3>Cross-module (Modulith CLOSED)</h3>
 * <p>users → {@code UserDirectoryApi} (core.identity OPEN). « staff actif du tenant » +
 * « tenant-admin » + destinataires notif → read-views SQL natives du repo (tables
 * {@code restaurant_staffs}/{@code restaurants}/{@code users}/{@code roles}). Aucun import de
 * {@code modules.restaurant}. Notification → {@link AnnouncementPublishedEvent} (Modulith) consommé
 * par {@code core.notification}. Temps réel → {@link AnnouncementPublisher} (STOMP).</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class AnnouncementService {

    private final AnnouncementRepository repo;
    private final AnnouncementReadRepository readRepo;
    private final UserDirectoryApi userDirectory;
    private final MembershipDirectoryApi membershipDirectory;
    private final ApplicationEventPublisher eventPublisher;
    private final AnnouncementPublisher announcementPublisher;

    private static final String DEFAULT_PRIORITY = "permanent";

    /**
     * Tenant « programme » du caller (membre) : sa 1ʳᵉ membership active (après le flip V95 un membre
     * a le home oneclick, mais conserve sa membership programme) sinon son tenant home (fallback
     * rétro-compatible : un staff/non-membre n'a pas de membership → home tenant, ancien comportement).
     * {@code null} si ni l'un ni l'autre.
     */
    private UUID callerProgramTenant(UUID caller) {
        return membershipDirectory.activeTenantIds(caller).stream().findFirst()
            .orElseGet(() -> userDirectory.tenantIdById(caller).orElse(null));
    }

    // ─── listForMe (annonces visibles du caller) ─────────────────────────────────

    /**
     * Annonces visibles du caller, scopées à SON tenant. tenant-admin → toutes (programmées +
     * archivées comprises) ; staff actif → publiées vivantes ; non-staff → vide. L'état lu/non-lu
     * du caller (par body_version) est joint sans N+1.
     *
     * <ul>
     *   <li>caller sans tenant → liste vide (la feature suppose un tenant).</li>
     * </ul>
     */
    public List<AnnouncementDto> listForMe() {
        UUID caller = requireCaller();
        // P3/V95 : un membre a le home oneclick après le flip → on scope au tenant de SA membership
        // programme (sinon home, fallback rétro-compatible pour staff/admin non flippés).
        UUID tenantId = callerProgramTenant(caller);
        boolean adminGlobal = SecurityHelper.isAdmin();
        if (tenantId == null) {
            // Un admin global sans tenant n'a pas de scope d'annonces (les annonces sont par-tenant).
            return List.of();
        }

        boolean tenantAdmin = adminGlobal || repo.isTenantAdmin(caller, tenantId);
        List<Announcement> rows;
        if (tenantAdmin) {
            rows = repo.findAllForAdmin(tenantId);
        } else if (repo.isActiveStaffOfTenant(caller, tenantId)) {
            rows = repo.findActiveForTenant(tenantId, Instant.now());
        } else {
            return List.of(); // ni admin ni staff du tenant → rien
        }

        Map<UUID, Integer> readVersions = readVersionsFor(caller, rows);
        Instant now = Instant.now();
        return rows.stream().map(a -> toDto(a, readVersions, now)).toList();
    }

    // ─── markRead (acquittement self) ────────────────────────────────────────────

    /**
     * Le caller marque une annonce comme lue (acquitte la {@code bodyVersion} fournie, ou la version
     * courante si null). Upsert idempotent. Le caller doit être staff/admin du tenant de l'annonce
     * (403 sinon). 404 si l'annonce n'existe pas / est soft-deletée.
     */
    @Transactional
    public AnnouncementDto markRead(UUID announcementId, Integer bodyVersion) {
        UUID caller = requireCaller();
        Announcement a = repo.findById(announcementId)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Announcement", announcementId));

        // Le caller doit appartenir au tenant de l'annonce (staff actif OU admin du tenant/global).
        if (!canReadInTenant(caller, a.getTenantId())) {
            throw new ForbiddenException(
                "Accès interdit : vous ne pouvez acquitter que les annonces de votre établissement");
        }

        int versionRead = bodyVersion != null ? bodyVersion : a.getBodyVersion();
        Instant now = Instant.now();
        AnnouncementRead read = readRepo.findByAnnouncementIdAndUserId(announcementId, caller)
            .map(existing -> {
                existing.setBodyVersionRead(versionRead);
                existing.setReadAt(now);
                return existing;
            })
            .orElseGet(() -> new AnnouncementRead(announcementId, caller, versionRead, now));
        readRepo.save(read);
        log.info("[announcement] mark-read (id={}, user={}, version={})", announcementId, caller, versionRead);

        return toDto(a, Map.of(announcementId, versionRead), now);
    }

    // ─── create (tenant-admin publie) ────────────────────────────────────────────

    /**
     * Crée une annonce. Écriture = tenant-admin du tenant du caller OU admin global (403 sinon).
     * {@code author} = caller ; {@code tenant} = tenant du caller. Applique les défauts (priority
     * permanent, pinned true, publishAt now). Enforce « max 1 épinglée par priorité » si épinglée.
     * Publie {@link AnnouncementPublishedEvent} (→ notif staff) UNIQUEMENT si l'annonce est déjà
     * publiée (publish_at <= now) + push STOMP. Retourne le {@link AnnouncementDto}.
     *
     * <ul>
     *   <li>caller sans tenant → 400.</li>
     *   <li>caller non tenant-admin/admin → 403.</li>
     * </ul>
     */
    @Transactional
    public AnnouncementDto create(CreateAnnouncementDto dto) {
        UUID caller = requireCaller();
        UUID tenantId = userDirectory.tenantIdById(caller)
            .orElseThrow(() -> new BadRequestException(
                "Aucun tenant associé à votre compte — la publication d'annonces n'est pas disponible"));
        requireTenantAdmin(caller, tenantId);

        String priority = normalizePriority(dto.priority());
        boolean pinned = dto.pinned() == null || dto.pinned(); // défaut true
        Instant publishAt = dto.publishAt() != null ? dto.publishAt() : Instant.now();

        Announcement saved = repo.save(new Announcement(
            UUID.randomUUID(), tenantId, caller,
            dto.title().trim(), dto.body().trim(),
            trimToNull(dto.imageUrl()), priority, pinned, publishAt));
        log.info("[announcement] create (id={}, tenant={}, author={}, priority={}, pinned={}, publishAt={})",
            saved.getId(), tenantId, caller, priority, pinned, publishAt);

        if (pinned) {
            enforceMaxPinned(saved);
        }

        Instant now = Instant.now();
        AnnouncementDto out = toDto(saved, Map.of(), now);

        // Notif staff + push STOMP — uniquement si DÉJÀ publiée (une programmée notifie via cron, V1 hors scope).
        if (saved.isPublished(now)) {
            publishNotificationAndStomp(saved, out);
        }
        return out;
    }

    // ─── update (tenant-admin édite) ─────────────────────────────────────────────

    /**
     * Édite une annonce. Écriture = tenant-admin/admin du tenant de l'annonce (403 sinon). Si le
     * {@code body} change → {@code bodyVersion}++ + purge des acquittements (ré-acquittement requis).
     * Re-enforce « max 1 épinglée par priorité » si l'annonce reste/devient épinglée. Re-push STOMP.
     * Re-notifie le staff uniquement si le body a changé ET que l'annonce est publiée (nouvelle
     * version à acquitter). 404 si introuvable / soft-deletée.
     */
    @Transactional
    public AnnouncementDto update(UUID announcementId, UpdateAnnouncementDto dto) {
        UUID caller = requireCaller();
        Announcement a = repo.findById(announcementId)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Announcement", announcementId));
        requireTenantAdmin(caller, a.getTenantId());

        String priority = normalizePriority(dto.priority());
        boolean pinned = dto.pinned() == null || dto.pinned();
        Instant publishAt = dto.publishAt() != null ? dto.publishAt() : a.getPublishAt();

        boolean bodyChanged = a.applyEdit(
            dto.title().trim(), dto.body().trim(), trimToNull(dto.imageUrl()), priority, pinned, publishAt);
        Announcement saved = repo.save(a);

        if (bodyChanged) {
            // Bump body_version déjà fait par applyEdit → purge les acquittements (re-acknowledgement).
            readRepo.deleteByAnnouncementId(announcementId);
            log.info("[announcement] update body changed (id={}) → body_version={} + reset reads",
                announcementId, saved.getBodyVersion());
        }
        if (saved.isPinned() && saved.isLive()) {
            enforceMaxPinned(saved);
        }
        log.info("[announcement] update (id={}, by={}, priority={}, pinned={})",
            announcementId, caller, priority, saved.isPinned());

        Instant now = Instant.now();
        AnnouncementDto out = toDto(saved, Map.of(), now);

        // Re-notif staff seulement si nouvelle version à acquitter (body changé) ET publiée.
        if (bodyChanged && saved.isPublished(now)) {
            publishNotificationAndStomp(saved, out);
        } else {
            announcementPublisher.publish(saved.getTenantId(), out); // push live (édition mineure) sans notif
        }
        return out;
    }

    // ─── archive / softDelete (tenant-admin) ─────────────────────────────────────

    /** Archive une annonce (sort de la bannière, reste visible admin). Admin du tenant only. */
    @Transactional
    public AnnouncementDto archive(UUID announcementId) {
        UUID caller = requireCaller();
        Announcement a = repo.findById(announcementId)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Announcement", announcementId));
        requireTenantAdmin(caller, a.getTenantId());
        a.archive(Instant.now());
        Announcement saved = repo.save(a);
        log.info("[announcement] archive (id={}, by={})", announcementId, caller);
        return toDto(saved, Map.of(), Instant.now());
    }

    /** Soft-delete une annonce (invisible de tous). Admin du tenant only. Idempotent. */
    @Transactional
    public void softDelete(UUID announcementId) {
        UUID caller = requireCaller();
        Announcement a = repo.findById(announcementId).orElse(null);
        if (a == null || a.getDeletedAt() != null) {
            return; // déjà supprimée / inexistante → idempotent (pas d'erreur)
        }
        requireTenantAdmin(caller, a.getTenantId());
        a.softDelete(Instant.now());
        repo.save(a);
        log.info("[announcement] soft-delete (id={}, by={})", announcementId, caller);
    }

    // ─── helpers métier ──────────────────────────────────────────────────────────

    /** Désépingle les autres annonces vivantes de la même priorité/tenant (max 1 épinglée). */
    private void enforceMaxPinned(Announcement pinnedAnnouncement) {
        int unpinned = repo.unpinOthers(
            pinnedAnnouncement.getTenantId(), pinnedAnnouncement.getPriority(), pinnedAnnouncement.getId());
        if (unpinned > 0) {
            log.info("[announcement] enforce-max-pinned (kept={}, priority={}, unpinned={})",
                pinnedAnnouncement.getId(), pinnedAnnouncement.getPriority(), unpinned);
        }
    }

    /** Résout les destinataires staff + publie event notif + push STOMP. */
    private void publishNotificationAndStomp(Announcement saved, AnnouncementDto out) {
        List<UUID> recipients = repo.findStaffRecipientIds(saved.getTenantId(), saved.getAuthorId());
        eventPublisher.publishEvent(new AnnouncementPublishedEvent(
            saved.getId(), saved.getTenantId(), saved.getAuthorId(),
            recipients, saved.getTitle(), saved.getPriority(), Instant.now()));
        announcementPublisher.publish(saved.getTenantId(), out);
    }

    // ─── helpers ABAC ──────────────────────────────────────────────────────────────

    private UUID requireCaller() {
        UUID caller = SecurityHelper.currentUserId();
        if (caller == null) {
            throw new ForbiddenException("Authentification requise");
        }
        return caller;
    }

    /** 403 sauf si le caller est tenant-admin du tenant OU admin global. */
    private void requireTenantAdmin(UUID caller, UUID tenantId) {
        if (SecurityHelper.isAdmin()) return;
        if (repo.isTenantAdmin(caller, tenantId)) return;
        throw new ForbiddenException(
            "Accès interdit : seul un administrateur du tenant peut gérer les annonces");
    }

    /** true si le caller peut lire/acquitter dans ce tenant (staff actif OU admin du tenant/global). */
    private boolean canReadInTenant(UUID caller, UUID tenantId) {
        if (SecurityHelper.isAdmin()) return true;
        if (repo.isTenantAdmin(caller, tenantId)) return true;
        return repo.isActiveStaffOfTenant(caller, tenantId);
    }

    /** Normalise/valide la priorité (défaut permanent). 400 si valeur non reconnue. */
    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) return DEFAULT_PRIORITY;
        String p = priority.trim();
        if (!"urgent".equals(p) && !"permanent".equals(p)) {
            throw new BadRequestException("priority invalide (urgent|permanent)");
        }
        return p;
    }

    /** État lu (body_version acquittée) du caller pour la page d'annonces — 0 N+1. */
    private Map<UUID, Integer> readVersionsFor(UUID caller, List<Announcement> rows) {
        if (rows.isEmpty()) return Map.of();
        List<UUID> ids = rows.stream().map(Announcement::getId).toList();
        Map<UUID, Integer> map = new HashMap<>();
        for (Object[] row : readRepo.findReadAnnouncementIdsForUser(caller, ids)) {
            map.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return map;
    }

    /** Mapping entité → DTO (enrichit nom de l'auteur + état lu du caller). */
    private AnnouncementDto toDto(Announcement a, Map<UUID, Integer> readVersions, Instant now) {
        UserName author = userDirectory.nameById(a.getAuthorId()).orElse(null);
        Integer readVersion = readVersions.get(a.getId());
        boolean readByMe = readVersion != null && readVersion >= a.getBodyVersion();
        return new AnnouncementDto(
            a.getId(),
            a.getTenantId(),
            a.getAuthorId(),
            author != null ? author.firstName() : null,
            author != null ? author.lastName() : null,
            a.getTitle(),
            a.getBody(),
            a.getImageUrl(),
            a.getPriority(),
            a.isPinned(),
            a.getPublishAt(),
            a.isPublished(now),
            a.getArchivedAt(),
            a.getDeletedAt(),
            a.getBodyVersion(),
            readByMe,
            a.getCreatedAt(),
            a.getUpdatedAt());
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
