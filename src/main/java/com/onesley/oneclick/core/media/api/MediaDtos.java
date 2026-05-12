package com.onesley.oneclick.core.media.api;

import jakarta.validation.constraints.NotBlank;
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
        @NotBlank String entityType,
        @NotNull UUID entityId,
        @NotBlank String url,
        @NotNull @Pattern(regexp = "^(image|video|audio|pdf)$") String mediaType,
        String mimeType,
        Long sizeBytes,
        Integer sortOrder
    ) {}

    // ─── FileAttachment ──────────────────────────────────────────────────────

    public record FileDto(UUID id, String entityType, UUID entityId, String path, String mimeType,
                          Long sizeBytes, String originalName, Instant createdAt, UUID createdById) {}

    public record FileCreateDto(
        @NotBlank String entityType,
        @NotNull UUID entityId,
        @NotBlank String path,
        String mimeType,
        Long sizeBytes,
        String originalName
    ) {}
}
