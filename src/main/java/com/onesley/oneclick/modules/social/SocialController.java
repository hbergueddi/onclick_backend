package com.onesley.oneclick.modules.social;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.onesley.oneclick.modules.social.internal.SocialService;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.exception.ForbiddenException;

import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.social.api.SocialDtos.*;
import lombok.RequiredArgsConstructor;

/**
 * Bug 32 (Batch D RBAC v2) — RBAC v2 senior strict hasAuthority('VERB:COMMUNITY')
 */
@RestController
@RequestMapping("/api/social")
@Tag(name = "Social", description = "Amitiés + parrainages (§8)")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService service;

    public record ActivateReferralDto(UUID referredUserId) {}

    // ─── Friendships ─────────────────────────────────────────────────────────

    @GetMapping("/friendships/by-user/{userId}")
    @Operation(summary = "Liste des amis acceptés d'un user")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<FriendshipDto> findFriendsOf(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return service.findFriendsOf(userId);
    }

    @GetMapping("/friendships/pending/by-user/{userId}")
    @Operation(summary = "Demandes d'amitié REÇUES (pending) par un user — Pocket « Demandes reçues »")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<FriendshipDto> findPendingReceived(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return service.findPendingReceivedBy(userId);
    }

    @GetMapping("/friendships/sent/by-user/{userId}")
    @Operation(summary = "Demandes d'amitié ENVOYÉES (pending/declined) par un user — Pocket « Invitations envoyées · Amitié »")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<FriendshipDto> findSent(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return service.findSentBy(userId);
    }

    @PostMapping("/friendships")
    @Operation(summary = "Demande d'amitié (pending)")
    @PreAuthorize("hasAuthority('CREATE:COMMUNITY')")
    public ResponseEntity<FriendshipDto> request(@Valid @RequestBody FriendshipCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.request(dto));
    }

    @GetMapping("/users/by-phone")
    @Operation(
        summary = "Découverte sociale — profil public minimal par téléphone (invitation/ajout d'ami).",
        description = "Alternative scoped à /api/users/by-phone (VIEW:USERS, admin). Gardé VIEW:COMMUNITY " +
                      "(que le CLIENT détient) ; ne renvoie qu'un profil d'affichage minimal, 404 si introuvable."
    )
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public PublicProfileDto findUserByPhone(@RequestParam String phone) {
        return service.findUserByPhone(phone);
    }

    @PostMapping("/contact-import")
    @Operation(
        summary = "Import du carnet d'adresses → utilisateurs OneClick correspondants (quota 10/jour/user).",
        description = "Soumet une liste de téléphones/emails et renvoie les profils publics minimaux des " +
                      "utilisateurs OneClick correspondants. Quota : 10 imports / fenêtre 24 h / user (429 au-delà). " +
                      "Gardé CREATE:COMMUNITY (que le CLIENT détient, V38) car l'import journalise une ligne ; " +
                      "ABAC self-scope dans le service (jamais un userId arbitraire)."
    )
    @PreAuthorize("hasAuthority('CREATE:COMMUNITY')")
    public ContactImportResultDto importContacts(@Valid @RequestBody ContactImportRequestDto dto) {
        return service.importContacts(dto);
    }

    @GetMapping("/friends-count/by-user/{userId}")
    @Operation(
        summary = "Nombre d'amis acceptés d'un user — pilote l'affichage « X/50 » + le plafond.",
        description = "ABAC self/admin. Permet au front de connaître l'état du plafond d'amis sans deviner " +
                      "le compteur serveur."
    )
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public java.util.Map<String, Long> friendsCount(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return java.util.Map.of("count", service.acceptedFriendCount(userId));
    }

    @PatchMapping("/friendships/{id}/accept")
    @Operation(summary = "Accepte une demande d'amitié")
    @PreAuthorize("hasAuthority('UPDATE:COMMUNITY')")
    public FriendshipDto accept(@PathVariable UUID id) { return service.accept(id); }

    @PatchMapping("/friendships/{id}/decline")
    @Operation(summary = "Refuse une demande d'amitié")
    @PreAuthorize("hasAuthority('UPDATE:COMMUNITY')")
    public FriendshipDto decline(@PathVariable UUID id) { return service.decline(id); }

    @DeleteMapping("/friendships/{id}")
    @Operation(summary = "Retire définitivement une amitié (Pocket « Retirer cet ami ») — partie ou admin")
    @PreAuthorize("hasAuthority('DELETE:COMMUNITY')")
    public ResponseEntity<Void> deleteFriendship(@PathVariable UUID id) {
        service.deleteFriendship(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Referrals ───────────────────────────────────────────────────────────

    @GetMapping("/referrals")
    @Operation(summary = "Liste paginée de tous les parrainages (admin platform-wide)")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public org.springframework.data.domain.Page<ReferralDto> findAllReferrals(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // Liste platform-wide → admin only ; un non-admin passe par /referrals/by-referrer/{self}.
        if (!SecurityHelper.isAdmin()) {
            throw new ForbiddenException("Liste globale des parrainages réservée à l'administration");
        }
        return service.findAllReferrals(page, size);
    }

    @GetMapping("/referrals/by-referrer/{referrerId}")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<ReferralDto> findByReferrer(@PathVariable UUID referrerId) {
        SecurityHelper.requireOwnerOrAdmin(referrerId);
        return service.findByReferrer(referrerId);
    }

    @PostMapping("/referrals")
    @PreAuthorize("hasAuthority('CREATE:COMMUNITY')")
    public ResponseEntity<ReferralDto> create(@Valid @RequestBody ReferralCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PatchMapping("/referrals/{id}/activate")
    @Operation(summary = "Active un parrainage (lors du signup du parrainé)")
    @PreAuthorize("hasAuthority('UPDATE:COMMUNITY')")
    public ReferralDto activate(@PathVariable UUID id, @RequestBody ActivateReferralDto body) {
        return service.activate(id, body.referredUserId());
    }

    // ─── Favoris (user_favorites) ────────────────────────────────────────────

    @GetMapping("/favorites/by-user/{userId}")
    @Operation(summary = "Liste des restaurants favoris d'un user")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<UserFavoriteDto> findFavoritesOf(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return service.findFavoritesOf(userId);
    }

    @PostMapping("/favorites")
    @Operation(summary = "Ajoute un restaurant aux favoris d'un user")
    @PreAuthorize("hasAuthority('CREATE:COMMUNITY')")
    public ResponseEntity<UserFavoriteDto> addFavorite(@Valid @RequestBody UserFavoriteCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addFavorite(dto));
    }

    @DeleteMapping("/favorites/{id}")
    @Operation(summary = "Retire un favori")
    @PreAuthorize("hasAuthority('DELETE:COMMUNITY')")
    public ResponseEntity<Void> removeFavorite(@PathVariable UUID id) {
        service.removeFavorite(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Friend groups (squads/teams Pocket) ─────────────────────────────────

    @GetMapping("/friend-groups/by-owner/{ownerId}")
    @Operation(summary = "Groupes possédés par un user")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<FriendGroupDto> findGroupsByOwner(@PathVariable UUID ownerId) {
        SecurityHelper.requireOwnerOrAdmin(ownerId);
        return service.findGroupsByOwner(ownerId);
    }

    @GetMapping("/friend-groups/by-member/{userId}")
    @Operation(summary = "Groupes auxquels un user appartient")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<FriendGroupDto> findGroupsByMember(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return service.findGroupsByMember(userId);
    }

    @GetMapping("/friend-groups/{id}")
    @Operation(summary = "Détail d'un groupe (owner, membres, admin)")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public FriendGroupDto findGroupById(@PathVariable UUID id) {
        return service.findGroupById(id);
    }

    @PostMapping("/friend-groups")
    @Operation(summary = "Crée un groupe (owner = current user)")
    @PreAuthorize("hasAuthority('CREATE:COMMUNITY')")
    public ResponseEntity<FriendGroupDto> createGroup(@Valid @RequestBody FriendGroupCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createGroup(dto));
    }

    @PatchMapping("/friend-groups/{id}")
    @Operation(summary = "Met à jour nom/description/avatar d'un groupe")
    @PreAuthorize("hasAuthority('UPDATE:COMMUNITY')")
    public FriendGroupDto updateGroup(@PathVariable UUID id, @Valid @RequestBody FriendGroupUpdateDto dto) {
        return service.updateGroup(id, dto);
    }

    @DeleteMapping("/friend-groups/{id}")
    @Operation(summary = "Soft delete d'un groupe")
    @PreAuthorize("hasAuthority('DELETE:COMMUNITY')")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID id) {
        service.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/friend-groups/{groupId}/members")
    @Operation(summary = "Liste des membres d'un groupe")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<FriendGroupMemberDto> findGroupMembers(@PathVariable UUID groupId) {
        return service.findGroupMembers(groupId);
    }

    @PostMapping("/friend-groups/{groupId}/members")
    @Operation(summary = "Ajoute un membre au groupe (owner/admin du groupe)")
    @PreAuthorize("hasAuthority('UPDATE:COMMUNITY')")
    public ResponseEntity<FriendGroupMemberDto> addGroupMember(
        @PathVariable UUID groupId,
        @Valid @RequestBody FriendGroupMemberAddDto dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addGroupMember(groupId, dto));
    }

    @DeleteMapping("/friend-groups/{groupId}/members/{friendId}")
    @Operation(summary = "Retire un membre du groupe (owner/admin du groupe, ou self)")
    @PreAuthorize("hasAuthority('UPDATE:COMMUNITY')")
    public ResponseEntity<Void> removeGroupMember(
        @PathVariable UUID groupId,
        @PathVariable UUID friendId
    ) {
        service.removeGroupMember(groupId, friendId);
        return ResponseEntity.noContent().build();
    }
}
