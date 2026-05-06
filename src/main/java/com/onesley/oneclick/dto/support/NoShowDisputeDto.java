package com.onesley.oneclick.dto.support;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code no_show_disputes} (généré par scripts/scaffold-jpa.mjs).
 */
public record NoShowDisputeDto(
    UUID id,
    UUID reservationId,
    UUID clientId,
    String description,
    String photoUrl,
    Boolean isRecontestation,
    String status,
    String escalationPhase,
    String resolutionNote,
    Instant resolvedAt,
    UUID resolvedBy,
    UUID supportTicketId,
    Instant createdAt
) {
}
