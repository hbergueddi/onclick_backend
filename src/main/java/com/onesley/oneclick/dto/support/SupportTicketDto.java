package com.onesley.oneclick.dto.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO pour {@code support_tickets} (généré par scripts/scaffold-jpa.mjs).
 */
public record SupportTicketDto(
    UUID id,
    UUID clientId,
    String category,
    String subject,
    String message,
    List<String> photos,
    String status,
    String lastReply,
    Instant createdAt,
    Instant updatedAt,
    UUID restaurantId,
    String ticketType,
    Boolean escalatedToAdmin,
    String priority,
    String resolutionLevel,
    Boolean aiHandled,
    String aiSummary,
    UUID createdBy,
    UUID modifiedBy
) {
}
