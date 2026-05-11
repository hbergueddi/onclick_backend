package com.onesley.oneclick.modules.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class SocialDtos {

    private SocialDtos() {}

    public record FriendshipDto(UUID id, UUID user1Id, UUID user2Id, String status, Instant acceptedAt, Instant createdAt) {
        public static FriendshipDto from(Friendship f) {
            return new FriendshipDto(f.getId(), f.getUser1Id(), f.getUser2Id(), f.getStatus(), f.getAcceptedAt(), f.getCreatedAt());
        }
    }

    public record FriendshipCreateDto(@NotNull UUID user1Id, @NotNull UUID user2Id) {}

    public record ReferralDto(UUID id, UUID referrerId, UUID referredUserId, String referralCode, String status, Instant activatedAt, Instant createdAt) {
        public static ReferralDto from(Referral r) {
            return new ReferralDto(r.getId(), r.getReferrerId(), r.getReferredUserId(), r.getReferralCode(), r.getStatus(), r.getActivatedAt(), r.getCreatedAt());
        }
    }

    public record ReferralCreateDto(@NotNull UUID referrerId, @NotBlank String referralCode) {}
}
