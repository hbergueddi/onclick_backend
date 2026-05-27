package com.onesley.oneclick.core.media.api;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de modération d'un média (V46). Générique : porte {@code entityType} +
 * {@code entityId} (le nom de l'entité — ex: restaurant — est résolu par
 * l'appelant). {@code moderationStatus} ∈ pending|approved|rejected.
 */
public record MediaModerationDto(
    UUID id,
    String entityType,
    UUID entityId,
    String mediaType,
    String url,
    String moderationStatus,
    Instant createdAt
) {
}
