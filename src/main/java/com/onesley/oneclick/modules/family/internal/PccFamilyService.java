package com.onesley.oneclick.modules.family.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi.UserName;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.exception.UnprocessableException;
import com.onesley.oneclick.modules.family.api.PccFamilyDtos.AddFamilyMemberDto;
import com.onesley.oneclick.modules.family.api.PccFamilyDtos.FamilyMemberDto;
import com.onesley.oneclick.modules.family.api.PccFamilyDtos.PointsHistoryEntryDto;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.FamilyMemberAddedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Logique « Ma Famille » (PCC Lot 5) — port fidèle des 4 RPCs legacy SECURITY DEFINER vers
 * un service Spring avec ABAC self-scope.
 *
 * <h3>Mapping RPC legacy → méthode</h3>
 * <ul>
 *   <li>{@code add_pcc_family_member(identifier, relation)} → {@link #addFamilyMember}</li>
 *   <li>{@code list_pcc_family()} → {@link #listMyFamily}</li>
 *   <li>{@code get_family_member_points_history(target)} → {@link #memberPointsHistory}</li>
 *   <li>{@code remove_pcc_family_member(relation_id)} → {@link #removeFamilyMember}</li>
 * </ul>
 *
 * <h3>ABAC self-scope</h3>
 * <p>Le caller (A) = {@code SecurityHelper.currentUserId()} (sub du JWT). Toutes les
 * opérations sont scopées au caller : on ne lit/écrit JAMAIS la famille d'un autre membre.
 * Le scope <b>tenant</b> est celui du caller (résolu via {@code UserDirectoryApi}), pas un
 * UUID en dur → générique multi-tenant (vs legacy PCC-only palmeraie).</p>
 *
 * <h3>Cross-module (Modulith CLOSED)</h3>
 * <p>users → {@code UserDirectoryApi} (contrat typé, core.identity OPEN). points fidélité →
 * read-views SQL natives du repo. Aucun import des modules loyalty/restaurant/identity.internal.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class PccFamilyService {

    /** Limite de proches par caller (legacy : trigger BEFORE INSERT « PCC_FAMILY_MAX_REACHED »). */
    static final int MAX_FAMILY_MEMBERS = 10;

    private final PccFamilyMemberRepository repo;
    private final UserDirectoryApi userDirectory;
    private final ApplicationEventPublisher eventPublisher;

    // ─── add (3 méthodes email / code OC- / téléphone) ──────────────────────────

    /**
     * Ajoute un proche à la liste famille du caller. Résout {@code identifier} (email / code
     * {@code OC-…} / téléphone) dans le <b>tenant du caller</b>.
     *
     * <ul>
     *   <li>TARGET_NOT_FOUND → {@link NotFoundException} (404) si aucun membre actif du tenant.</li>
     *   <li>CANNOT_ADD_SELF → {@link BadRequestException} (400) si le proche = le caller.</li>
     *   <li>idempotent : si le lien (A→B) existe déjà → retourne l'existant ({@code status="already_added"}),
     *       SANS re-notifier ni re-vérifier la limite (le legacy renvoyait {@code already_added}).</li>
     *   <li>limite atteinte → {@link UnprocessableException} (422) si le caller a déjà 10 proches.</li>
     * </ul>
     *
     * <p>Sur une création réelle : publie {@link FamilyMemberAddedEvent} (→ notification au proche).</p>
     */
    @Transactional
    public FamilyMemberDto addFamilyMember(AddFamilyMemberDto dto) {
        UUID caller = requireCaller();
        UUID tenantId = userDirectory.tenantIdById(caller)
            .orElseThrow(() -> new BadRequestException(
                "Aucun tenant associé à votre compte — la liste famille n'est pas disponible"));

        UserName target = userDirectory.findByIdentifier(dto.identifier(), tenantId)
            .orElseThrow(() -> new NotFoundException(
                "Aucun membre trouvé avec cet identifiant dans votre espace"));

        if (target.id().equals(caller)) {
            throw new BadRequestException("Vous ne pouvez pas vous ajouter vous-même");
        }

        // Idempotent : relation déjà existante → on renvoie l'existant sans rien modifier.
        var existing = repo.findByMemberIdAndRelatedMemberId(caller, target.id());
        if (existing.isPresent()) {
            return toDto(existing.get(), target, "already_added");
        }

        // Limite 10 — contrôlée AVANT l'insert (legacy : trigger BEFORE INSERT).
        if (repo.countByMemberId(caller) >= MAX_FAMILY_MEMBERS) {
            throw new UnprocessableException("Limite de 10 membres atteinte");
        }

        PccFamilyMember saved = repo.save(
            new PccFamilyMember(UUID.randomUUID(), caller, target.id(), trimToNull(dto.relation())));
        log.info("[family] add (member={}, related={}, relation={})",
            caller, target.id(), saved.getRelation());

        // Notif au proche, server-side (le CLIENT n'a pas CREATE:NOTIFICATIONS).
        eventPublisher.publishEvent(new FamilyMemberAddedEvent(caller, target.id(), Instant.now()));

        return toDto(saved, target, "added");
    }

    // ─── list (mes proches + total points restants chacun) ──────────────────────

    /**
     * Liste « ma famille » du caller (du plus récent au plus ancien), enrichie du nom/avatar
     * du proche (UserDirectoryApi) et de son total de points restants dans le tenant courant
     * (read-view native). Un caller sans tenant → liste vide.
     */
    public List<FamilyMemberDto> listMyFamily() {
        UUID caller = requireCaller();
        UUID tenantId = userDirectory.tenantIdById(caller).orElse(null);
        List<PccFamilyMember> links = repo.findByMemberIdOrderByCreatedAtDesc(caller);
        if (links.isEmpty() || tenantId == null) {
            return List.of();
        }
        final UUID tid = tenantId;
        return links.stream().map(link -> {
            UserName name = userDirectory.nameById(link.getRelatedMemberId()).orElse(null);
            long total = repo.sumRemainingPointsByClientInTenant(link.getRelatedMemberId(), tid);
            return toDto(link, name, total, null);
        }).toList();
    }

    // ─── points-history (historique du proche SI relation existe) ────────────────

    /**
     * Historique complet des points fidélité d'un proche, restreint au tenant courant.
     * <p>Contrôle d'accès (legacy NO_FAMILY_RELATION) : si le caller n'a PAS ajouté
     * {@code targetId} à sa famille → {@link ForbiddenException} (403). Sinon, read-view native.</p>
     */
    public List<PointsHistoryEntryDto> memberPointsHistory(UUID targetId) {
        UUID caller = requireCaller();
        if (repo.findByMemberIdAndRelatedMemberId(caller, targetId).isEmpty()) {
            throw new ForbiddenException(
                "Vous n'êtes pas autorisé à voir cet historique (ce membre n'est pas dans votre famille)");
        }
        UUID tenantId = userDirectory.tenantIdById(caller).orElse(null);
        if (tenantId == null) {
            return List.of();
        }
        return repo.pointsHistoryByClientInTenant(targetId, tenantId).stream()
            .map(v -> new PointsHistoryEntryDto(
                v.getId(), v.getRestaurantId(), v.getRestaurantName(),
                v.getPoints(), v.getAmountTtc(), v.getReason(),
                v.getEarnedAt(), v.getRemainingPoints(), v.getExpiresAt()))
            .toList();
    }

    // ─── remove (caller = A OU B) ────────────────────────────────────────────────

    /**
     * Retire un lien famille. Le caller doit être A (propriétaire) OU B (le proche qui se
     * retire lui-même) — legacy NOT_AUTHORIZED. {@link NotFoundException} (404) si le lien
     * n'existe pas ; {@link ForbiddenException} (403) si le caller n'est ni A ni B.
     */
    @Transactional
    public void removeFamilyMember(UUID relationId) {
        UUID caller = requireCaller();
        PccFamilyMember link = repo.findById(relationId)
            .orElseThrow(() -> new NotFoundException("PccFamilyMember", relationId));
        if (!caller.equals(link.getMemberId()) && !caller.equals(link.getRelatedMemberId())) {
            throw new ForbiddenException(
                "Accès interdit : vous ne pouvez retirer que vos propres liens famille");
        }
        repo.delete(link);
        log.info("[family] remove (relation={}, by={})", relationId, caller);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────────

    private UUID requireCaller() {
        UUID caller = SecurityHelper.currentUserId();
        if (caller == null) {
            throw new ForbiddenException("Authentification requise");
        }
        return caller;
    }

    private FamilyMemberDto toDto(PccFamilyMember link, UserName name, String status) {
        return toDto(link, name, 0L, status);
    }

    private FamilyMemberDto toDto(PccFamilyMember link, UserName name, long totalRemainingPoints, String status) {
        return new FamilyMemberDto(
            link.getId(),
            link.getRelatedMemberId(),
            name != null ? name.firstName() : null,
            name != null ? name.lastName() : null,
            name != null ? name.email() : null,
            name != null ? name.avatarUrl() : null,
            link.getRelation(),
            totalRemainingPoints,
            link.getCreatedAt(),
            status);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
