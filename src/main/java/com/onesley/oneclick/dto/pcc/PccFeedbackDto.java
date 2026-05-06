package com.onesley.oneclick.dto.pcc;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code pcc_feedbacks} (généré par scripts/scaffold-jpa.mjs).
 */
public record PccFeedbackDto(
    UUID id,
    UUID memberId,
    String sentiment,
    String category,
    String comment,
    String replyText,
    UUID replyBy,
    Instant replyAt,
    Boolean replyReadByMember,
    Instant createdAt,
    Instant updatedAt,
    UUID targetRestaurantId
) {
}
