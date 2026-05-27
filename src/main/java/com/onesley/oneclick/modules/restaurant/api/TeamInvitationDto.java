package com.onesley.oneclick.modules.restaurant.api;

import java.time.Instant;
import java.util.UUID;

/** DTO public d'une invitation d'équipe. Statut EN : pending|accepted|disabled. */
public record TeamInvitationDto(
    UUID id,
    UUID restaurantId,
    UUID invitedById,
    String firstName,
    String lastName,
    String phone,
    String role,
    String status,
    Instant expiresAt,
    Instant createdAt
) {
}
