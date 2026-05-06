package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code team_invitations} (généré par scripts/scaffold-jpa.mjs).
 */
public record TeamInvitationDto(
    UUID id,
    UUID restaurantId,
    UUID invitedBy,
    String firstName,
    String lastName,
    String phone,
    String role,
    String status,
    Instant expiresAt,
    Instant createdAt
) {
}
