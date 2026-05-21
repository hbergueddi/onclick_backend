package com.onesley.oneclick.core.media.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs publics du module media.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Entity.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 */
public final class MediaDtos {

    private MediaDtos() {}

    // ─── Media (image/video/audio/pdf) ───────────────────────────────────────

    public record MediaDto(UUID id, String entityType, UUID entityId, String url, String mediaType,
                           String mimeType, Long sizeBytes, Integer sortOrder, Map<String, Object> metadata,
                           Instant createdAt) {}

    public record MediaCreateDto(
        @NotBlank @Size(min = 1, max = 64) String entityType,
        @NotNull UUID entityId,
        @NotBlank @Size(min = 1, max = 512) String url,
        @NotNull @Pattern(regexp = "^(image|video|audio|pdf)$") @Size(min = 1, max = 64) String mediaType,
        @Size(min = 1, max = 64) String mimeType,
        @PositiveOrZero Long sizeBytes,
        @PositiveOrZero Integer sortOrder
    ) {}

    // ─── FileAttachment ──────────────────────────────────────────────────────

    public record FileDto(UUID id, String entityType, UUID entityId, String path, String mimeType,
                          Long sizeBytes, String originalName, Instant createdAt, UUID createdById) {}

    public record FileCreateDto(
        @NotBlank @Size(min = 1, max = 64) String entityType,
        @NotNull UUID entityId,
        @NotBlank @Size(min = 1, max = 512) String path,
        @Size(min = 1, max = 64) String mimeType,
        @PositiveOrZero Long sizeBytes,
        @Size(min = 1, max = 128) String originalName
    ) {}
}
