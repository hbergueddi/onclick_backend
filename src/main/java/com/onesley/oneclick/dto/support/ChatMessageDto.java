package com.onesley.oneclick.dto.support;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code chat_messages} (généré par scripts/scaffold-jpa.mjs).
 */
public record ChatMessageDto(
    UUID id,
    UUID ticketId,
    UUID userId,
    String role,
    String content,
    Instant createdAt
) {
}
