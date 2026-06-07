package com.onesley.oneclick.modules.stories.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi.UserName;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.stories.api.PccStoryDtos.CreateStoryDto;
import com.onesley.oneclick.modules.stories.api.PccStoryDtos.StoryDto;
import com.onesley.oneclick.modules.stories.api.PccStoryDtos.StoryViewCountDto;
import com.onesley.oneclick.modules.stories.api.PccStoryDtos.UpdateStoryDto;
import com.onesley.oneclick.security.SecurityHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Logique « Stories » (PCC) — contenu éphémère type Instagram (un staff/owner publie, les membres
 * du tenant visionnent). Service Spring avec ABAC. GÉNÉRIQUE : tout est scopé au tenant DU CALLER
 * (résolu via {@code UserDirectoryApi}), jamais {@code slug='palmeraie'} en dur.
 *
 * <h3>ABAC</h3>
 * <ul>
 *   <li><b>Lecture</b> ({@link #listForMe}) : tenant du caller. Un staff/admin du tenant
 *       ({@code SecurityHelper.isStaffOrAdmin} + appartenance tenant) → TOUT le tenant (incl.
 *       programmées + expirées, pour pilotage). Un membre (CLIENT) → uniquement les stories vivantes
 *       et visibles ({@code publish_at <= now}, non expirées). Un caller hors tenant → liste vide
 *       (défense en profondeur ; l'endpoint exige VIEW:STORIES).</li>
 *   <li><b>Écriture</b> ({@link #create}/{@link #update}/{@link #softDelete}) : staff actif du
 *       tenant du caller OU admin global uniquement (403 sinon). Toute mutation est restreinte à une
 *       story DU tenant du caller (isolation cross-tenant).</li>
 * </ul>
 *
 * <h3>Cross-module (Modulith CLOSED)</h3>
 * <p>users → {@code UserDirectoryApi} (core.identity OPEN). « staff actif du tenant » → read-view SQL
 * native du repo (tables {@code restaurant_staffs}/{@code restaurants}). Aucun import de
 * {@code modules.restaurant}. <b>Pas d'event de notification ni de push STOMP</b> : une story est du
 * contenu (fetch normal côté membre), pas un dashboard temps réel — cf. package-info.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class PccStoryService {

    private final PccStoryRepository repo;
    private final PccStoryViewRepository viewRepo;
    private final UserDirectoryApi userDirectory;

    private static final String DEFAULT_MEDIA_TYPE = "image";
    private static final int DEFAULT_DURATION_S = 15;
    private static final int DEFAULT_SORT_ORDER = 0;

    // ─── listForMe (stories visibles du caller) ──────────────────────────────────

    /**
     * Stories visibles du caller, scopées à SON tenant. staff/admin du tenant → toutes (programmées
     * + expirées comprises, pour gestion) ; membre (CLIENT) → vivantes et visibles uniquement ;
     * caller sans tenant → vide.
     */
    public List<StoryDto> listForMe() {
        UUID caller = requireCaller();
        UUID tenantId = userDirectory.tenantIdById(caller).orElse(null);
        if (tenantId == null) {
            // Un admin global sans tenant n'a pas de scope de stories (les stories sont par-tenant).
            return List.of();
        }

        Instant now = Instant.now();
        List<PccStory> rows;
        if (canManageInTenant(caller, tenantId)) {
            rows = repo.findAllForStaff(tenantId);
        } else {
            // Membre du tenant (ou tout caller non-staff) → uniquement les stories visibles.
            rows = repo.findActiveForTenant(tenantId, now);
        }
        // Gap #7 — enrichit `viewed` (ring unread/read) : 1 requête batch des vues du caller.
        Set<UUID> viewedIds = rows.isEmpty()
            ? Set.of()
            : Set.copyOf(viewRepo.findViewedStoryIds(caller, rows.stream().map(PccStory::getId).toList()));
        return rows.stream().map(s -> toDto(s, now, viewedIds.contains(s.getId()))).toList();
    }

    // ─── markViewed (membre ouvre une story) ─────────────────────────────────────

    /**
     * Marque une story « vue » par le caller (Gap #7) — idempotent (1ère vue préservée).
     * Défense : la story doit exister, ne pas être supprimée, et appartenir au tenant du caller
     * (ou caller admin global). 404 si introuvable/supprimée ; 403 si cross-tenant non-admin.
     */
    @Transactional
    public void markViewed(UUID storyId) {
        UUID caller = requireCaller();
        PccStory s = repo.findById(storyId)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("PccStory", storyId));
        if (!SecurityHelper.isAdmin()) {
            UUID callerTenant = userDirectory.tenantIdById(caller).orElse(null);
            if (callerTenant == null || !callerTenant.equals(s.getTenantId())) {
                throw new ForbiddenException("Story hors de votre établissement");
            }
        }
        if (!viewRepo.existsByStoryIdAndUserId(storyId, caller)) {
            viewRepo.save(new PccStoryView(storyId, caller, Instant.now()));
        }
    }

    // ─── viewCounts (stats staff/admin « vue par X membres ») ─────────────────────

    /**
     * Nombre de vues par story du tenant du caller (Gap #7) — réservé au staff/admin du tenant
     * (un membre ne voit pas les stats). Caller sans tenant gérable → liste vide.
     */
    public List<StoryViewCountDto> viewCounts() {
        UUID caller = requireCaller();
        UUID tenantId = userDirectory.tenantIdById(caller).orElse(null);
        if (tenantId == null || !canManageInTenant(caller, tenantId)) {
            // Membre (ou admin global sans tenant) → pas de stats par-tenant.
            if (tenantId != null) {
                requireTenantStaff(caller, tenantId); // membre du tenant → 403 explicite
            }
            return List.of();
        }
        List<UUID> ids = repo.findAllForStaff(tenantId).stream().map(PccStory::getId).toList();
        if (ids.isEmpty()) return List.of();
        return viewRepo.countByStoryIds(ids).stream()
            .map(row -> new StoryViewCountDto((UUID) row[0], ((Number) row[1]).longValue()))
            .toList();
    }

    // ─── create (staff publie) ───────────────────────────────────────────────────

    /**
     * Crée une story. Écriture = staff actif du tenant du caller OU admin global (403 sinon).
     * {@code author} = caller ; {@code tenant} = tenant du caller. Applique les défauts (mediaType
     * image, durationS 15, sortOrder 0, publishAt now). Retourne le {@link StoryDto}.
     *
     * <ul>
     *   <li>caller sans tenant → 400.</li>
     *   <li>caller non staff/admin du tenant → 403.</li>
     *   <li>mediaUrl vide / mediaType invalide / expiresAt avant publishAt → 400 (garde-fou service).</li>
     * </ul>
     */
    @Transactional
    public StoryDto create(CreateStoryDto dto) {
        UUID caller = requireCaller();
        UUID tenantId = userDirectory.tenantIdById(caller)
            .orElseThrow(() -> new BadRequestException(
                "Aucun tenant associé à votre compte — la publication de stories n'est pas disponible"));
        requireTenantStaff(caller, tenantId);

        String mediaUrl = requireMediaUrl(dto.mediaUrl());
        String mediaType = normalizeMediaType(dto.mediaType());
        String caption = trimToNull(dto.caption());
        int durationS = dto.durationS() != null ? dto.durationS() : DEFAULT_DURATION_S;
        int sortOrder = dto.sortOrder() != null ? dto.sortOrder() : DEFAULT_SORT_ORDER;
        Instant publishAt = dto.publishAt() != null ? dto.publishAt() : Instant.now();
        Instant expiresAt = dto.expiresAt();
        requireValidWindow(publishAt, expiresAt);

        PccStory saved = repo.save(new PccStory(
            UUID.randomUUID(), tenantId, caller,
            mediaUrl, mediaType, caption, durationS, sortOrder, publishAt, expiresAt));
        log.info("[stories] create (id={}, tenant={}, author={}, type={}, publishAt={}, expiresAt={})",
            saved.getId(), tenantId, caller, mediaType, publishAt, expiresAt);

        return toDto(saved, Instant.now(), false);
    }

    // ─── update (staff édite) ────────────────────────────────────────────────────

    /**
     * Édite une story. Écriture = staff/admin du tenant de la story (403 sinon). Remplacement complet
     * des champs éditables (sémantique PATCH = état souhaité). 404 si introuvable / soft-deletée.
     */
    @Transactional
    public StoryDto update(UUID storyId, UpdateStoryDto dto) {
        UUID caller = requireCaller();
        PccStory s = repo.findById(storyId)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("PccStory", storyId));
        requireTenantStaff(caller, s.getTenantId());

        String mediaUrl = requireMediaUrl(dto.mediaUrl());
        String mediaType = normalizeMediaType(dto.mediaType());
        String caption = trimToNull(dto.caption());
        int durationS = dto.durationS() != null ? dto.durationS() : s.getDurationS();
        int sortOrder = dto.sortOrder() != null ? dto.sortOrder() : s.getSortOrder();
        Instant publishAt = dto.publishAt() != null ? dto.publishAt() : s.getPublishAt();
        Instant expiresAt = dto.expiresAt();
        requireValidWindow(publishAt, expiresAt);

        s.applyEdit(mediaUrl, mediaType, caption, durationS, sortOrder, publishAt, expiresAt);
        PccStory saved = repo.save(s);
        log.info("[stories] update (id={}, by={}, type={}, sortOrder={})",
            storyId, caller, mediaType, sortOrder);

        return toDto(saved, Instant.now(), false);
    }

    // ─── softDelete (staff supprime) ─────────────────────────────────────────────

    /** Soft-delete une story (invisible de tous). Staff/admin du tenant only. Idempotent. */
    @Transactional
    public void softDelete(UUID storyId) {
        UUID caller = requireCaller();
        PccStory s = repo.findById(storyId).orElse(null);
        if (s == null || s.getDeletedAt() != null) {
            return; // déjà supprimée / inexistante → idempotent (pas d'erreur)
        }
        requireTenantStaff(caller, s.getTenantId());
        s.softDelete(Instant.now());
        repo.save(s);
        log.info("[stories] soft-delete (id={}, by={})", storyId, caller);
    }

    // ─── helpers ABAC ────────────────────────────────────────────────────────────

    private UUID requireCaller() {
        UUID caller = SecurityHelper.currentUserId();
        if (caller == null) {
            throw new ForbiddenException("Authentification requise");
        }
        return caller;
    }

    /** 403 sauf si le caller est staff actif du tenant OU admin global. */
    private void requireTenantStaff(UUID caller, UUID tenantId) {
        if (canManageInTenant(caller, tenantId)) return;
        throw new ForbiddenException(
            "Accès interdit : seul le staff de votre établissement peut gérer les stories");
    }

    /**
     * true si le caller peut gérer / voir la totalité des stories du tenant : admin global OU
     * (acteur côté gestion {@code isStaffOrAdmin} ET staff actif du tenant). Le {@code isStaffOrAdmin}
     * écarte d'emblée un CLIENT (jamais staff) sans toucher la DB ; le {@code isActiveStaffOfTenant}
     * confirme l'appartenance au tenant (isolation cross-tenant pour un RESTAURATEUR d'un autre tenant).
     */
    private boolean canManageInTenant(UUID caller, UUID tenantId) {
        if (SecurityHelper.isAdmin()) return true;
        if (!SecurityHelper.isStaffOrAdmin()) return false;
        return repo.isActiveStaffOfTenant(caller, tenantId);
    }

    // ─── helpers validation ──────────────────────────────────────────────────────

    /** Normalise/valide le type de média (défaut image). 400 si valeur non reconnue. */
    private String normalizeMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) return DEFAULT_MEDIA_TYPE;
        String t = mediaType.trim();
        if (!"image".equals(t) && !"video".equals(t)) {
            throw new BadRequestException("mediaType invalide (image|video)");
        }
        return t;
    }

    /** mediaUrl obligatoire (en plus de Bean Validation, défense en profondeur). */
    private String requireMediaUrl(String mediaUrl) {
        String u = trimToNull(mediaUrl);
        if (u == null) {
            throw new BadRequestException("mediaUrl requis");
        }
        return u;
    }

    /** Fenêtre de visibilité cohérente : si expiresAt fourni, il doit être après publishAt. */
    private void requireValidWindow(Instant publishAt, Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(publishAt)) {
            throw new BadRequestException("expiresAt doit être postérieur à publishAt");
        }
    }

    // ─── mapping ─────────────────────────────────────────────────────────────────

    /** Mapping entité → DTO (enrichit nom de l'auteur + visibilité courante + {@code viewed}). */
    private StoryDto toDto(PccStory s, Instant now, boolean viewed) {
        UserName author = userDirectory.nameById(s.getAuthorId()).orElse(null);
        return new StoryDto(
            s.getId(),
            s.getTenantId(),
            s.getAuthorId(),
            author != null ? author.firstName() : null,
            author != null ? author.lastName() : null,
            s.getMediaUrl(),
            s.getMediaType(),
            s.getCaption(),
            s.getDurationS(),
            s.getSortOrder(),
            s.getPublishAt(),
            s.getExpiresAt(),
            s.isVisibleToMembers(now),
            s.getDeletedAt(),
            s.getCreatedAt(),
            s.getUpdatedAt(),
            viewed);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
