package com.onesley.oneclick.modules.social.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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

    public record FriendshipDto(UUID id, UUID user1Id, UUID user2Id, String status, Instant acceptedAt, Instant createdAt) {}

    public record FriendshipCreateDto(@NotNull UUID user1Id, @NotNull UUID user2Id) {}

    public record ReferralDto(UUID id, UUID referrerId, UUID referredUserId, String referralCode, String status, Instant activatedAt, Instant createdAt) {}

    public record ReferralCreateDto(@NotNull UUID referrerId, @NotBlank String referralCode) {}
}
