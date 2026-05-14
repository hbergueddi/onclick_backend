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

import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.social.api.SocialDtos.*;

@RestController
@RequestMapping("/api/social")
@Tag(name = "Social", description = "Amitiés + parrainages (§8)")
public class SocialController {

    private final SocialService service;

    public SocialController(SocialService service) {
        this.service = service;
    }

    public record ActivateReferralDto(UUID referredUserId) {}

    // ─── Friendships ─────────────────────────────────────────────────────────

    @GetMapping("/friendships/by-user/{userId}")
    @Operation(summary = "Liste des amis acceptés d'un user")
    @PreAuthorize("isAuthenticated()")
    public List<FriendshipDto> findFriendsOf(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return service.findFriendsOf(userId);
    }

    @PostMapping("/friendships")
    @Operation(summary = "Demande d'amitié (pending)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FriendshipDto> request(@Valid @RequestBody FriendshipCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.request(dto));
    }

    @PatchMapping("/friendships/{id}/accept")
    @Operation(summary = "Accepte une demande d'amitié")
    @PreAuthorize("isAuthenticated()")
    public FriendshipDto accept(@PathVariable UUID id) { return service.accept(id); }

    @PatchMapping("/friendships/{id}/decline")
    @Operation(summary = "Refuse une demande d'amitié")
    @PreAuthorize("isAuthenticated()")
    public FriendshipDto decline(@PathVariable UUID id) { return service.decline(id); }

    // ─── Referrals ───────────────────────────────────────────────────────────

    @GetMapping("/referrals")
    @Operation(summary = "Liste paginée de tous les parrainages (admin platform-wide)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public org.springframework.data.domain.Page<ReferralDto> findAllReferrals(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return service.findAllReferrals(page, size);
    }

    @GetMapping("/referrals/by-referrer/{referrerId}")
    @PreAuthorize("isAuthenticated()")
    public List<ReferralDto> findByReferrer(@PathVariable UUID referrerId) {
        SecurityHelper.requireOwnerOrAdmin(referrerId);
        return service.findByReferrer(referrerId);
    }

    @PostMapping("/referrals")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReferralDto> create(@Valid @RequestBody ReferralCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PatchMapping("/referrals/{id}/activate")
    @Operation(summary = "Active un parrainage (lors du signup du parrainé)")
    @PreAuthorize("isAuthenticated()")
    public ReferralDto activate(@PathVariable UUID id, @RequestBody ActivateReferralDto body) {
        return service.activate(id, body.referredUserId());
    }

    // ─── Favoris (user_favorites) ────────────────────────────────────────────

    @GetMapping("/favorites/by-user/{userId}")
    @Operation(summary = "Liste des restaurants favoris d'un user")
    @PreAuthorize("isAuthenticated()")
    public List<UserFavoriteDto> findFavoritesOf(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return service.findFavoritesOf(userId);
    }

    @PostMapping("/favorites")
    @Operation(summary = "Ajoute un restaurant aux favoris d'un user")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserFavoriteDto> addFavorite(@Valid @RequestBody UserFavoriteCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addFavorite(dto));
    }

    @DeleteMapping("/favorites/{id}")
    @Operation(summary = "Retire un favori")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeFavorite(@PathVariable UUID id) {
        service.removeFavorite(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Friend groups (squads/teams Pocket) ─────────────────────────────────

    @GetMapping("/friend-groups/by-owner/{ownerId}")
    @Operation(summary = "Groupes possédés par un user")
    @PreAuthorize("isAuthenticated()")
    public List<FriendGroupDto> findGroupsByOwner(@PathVariable UUID ownerId) {
        SecurityHelper.requireOwnerOrAdmin(ownerId);
        return service.findGroupsByOwner(ownerId);
    }

    @GetMapping("/friend-groups/by-member/{userId}")
    @Operation(summary = "Groupes auxquels un user appartient")
    @PreAuthorize("isAuthenticated()")
    public List<FriendGroupDto> findGroupsByMember(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return service.findGroupsByMember(userId);
    }

    @GetMapping("/friend-groups/{id}")
    @Operation(summary = "Détail d'un groupe (owner, membres, admin)")
    @PreAuthorize("isAuthenticated()")
    public FriendGroupDto findGroupById(@PathVariable UUID id) {
        return service.findGroupById(id);
    }

    @PostMapping("/friend-groups")
    @Operation(summary = "Crée un groupe (owner = current user)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FriendGroupDto> createGroup(@Valid @RequestBody FriendGroupCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createGroup(dto));
    }

    @PatchMapping("/friend-groups/{id}")
    @Operation(summary = "Met à jour nom/description/avatar d'un groupe")
    @PreAuthorize("isAuthenticated()")
    public FriendGroupDto updateGroup(@PathVariable UUID id, @Valid @RequestBody FriendGroupUpdateDto dto) {
        return service.updateGroup(id, dto);
    }

    @DeleteMapping("/friend-groups/{id}")
    @Operation(summary = "Soft delete d'un groupe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID id) {
        service.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/friend-groups/{groupId}/members")
    @Operation(summary = "Liste des membres d'un groupe")
    @PreAuthorize("isAuthenticated()")
    public List<FriendGroupMemberDto> findGroupMembers(@PathVariable UUID groupId) {
        return service.findGroupMembers(groupId);
    }

    @PostMapping("/friend-groups/{groupId}/members")
    @Operation(summary = "Ajoute un membre au groupe (owner/admin du groupe)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FriendGroupMemberDto> addGroupMember(
        @PathVariable UUID groupId,
        @Valid @RequestBody FriendGroupMemberAddDto dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addGroupMember(groupId, dto));
    }

    @DeleteMapping("/friend-groups/{groupId}/members/{friendId}")
    @Operation(summary = "Retire un membre du groupe (owner/admin du groupe, ou self)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeGroupMember(
        @PathVariable UUID groupId,
        @PathVariable UUID friendId
    ) {
        service.removeGroupMember(groupId, friendId);
        return ResponseEntity.noContent().build();
    }
}
