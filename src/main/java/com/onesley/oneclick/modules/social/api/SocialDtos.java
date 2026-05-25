package com.onesley.oneclick.modules.social.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs publics du module social.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Entity.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 */
public final class SocialDtos {

    private SocialDtos() {}

    /**
     * Amitié. Les champs {@code friend*} sont l'enrichissement serveur-side du profil
     * de l'AMI (l'autre user) relatif à l'appelant — peuplés par
     * {@code SocialService.findFriendsOf} (résolution via le domaine identity).
     * {@code null} dans les autres contextes (request/accept/decline).
     */
    public record FriendshipDto(UUID id, UUID user1Id, UUID user2Id, String status, Instant acceptedAt, Instant createdAt,
                                UUID friendId, String friendFirstName, String friendLastName, String friendAvatarUrl) {}

    public record FriendshipCreateDto(@NotNull UUID user1Id, @NotNull UUID user2Id) {}

    public record ReferralDto(UUID id, UUID referrerId, UUID referredUserId, String referralCode, String status, Instant activatedAt, Instant createdAt) {}

    public record ReferralCreateDto(@NotNull UUID referrerId, @NotBlank @Size(min = 1, max = 64) String referralCode) {}

    // ─── Favoris (user_favorites) ────────────────────────────────────────────

    /** Favori d'un user sur un restaurant (bouton ❤️ Pocket). */
    public record UserFavoriteDto(UUID id, UUID userId, UUID restaurantId, Instant createdAt) {}

    public record UserFavoriteCreateDto(@NotNull UUID userId, @NotNull UUID restaurantId) {}

    // ─── Friend groups (squads/teams) ────────────────────────────────────────

    /**
     * Groupe d'amis — réservation collective Pocket.
     * {@code memberCount} = total des entrées {@code friend_group_members}.
     */
    public record FriendGroupDto(
        UUID id,
        UUID ownerId,
        String name,
        String description,
        String avatarUrl,
        long memberCount,
        Instant createdAt
    ) {}

    public record FriendGroupCreateDto(
        @NotBlank @Size(max = 500) @Size(min = 1, max = 128) String name,
        @Size(min = 1, max = 1024) String description,
        @Size(min = 1, max = 512) String avatarUrl
    ) {}

    public record FriendGroupUpdateDto(
        @Size(max = 500) @Size(min = 1, max = 128) String name,
        @Size(min = 1, max = 1024) String description,
        @Size(min = 1, max = 512) String avatarUrl
    ) {}

    /** Junction users × friend_groups — appartenance + rôle. */
    public record FriendGroupMemberDto(
        UUID id,
        UUID friendGroupId,
        UUID friendId,
        String role,
        Instant joinedAt
    ) {}

    public record FriendGroupMemberAddDto(
        @NotNull UUID friendId,
        @Pattern(regexp = "^(owner|admin|member)$") @Size(min = 1, max = 64) String role
    ) {}
}
