package com.onesley.oneclick.core.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class MediaDtos {

    private MediaDtos() {}

    // ─── Media (image/video/audio/pdf) ───────────────────────────────────────

    public record MediaDto(UUID id, String entityType, UUID entityId, String url, String mediaType,
                           String mimeType, Long sizeBytes, Integer sortOrder, Map<String, Object> metadata,
                           Instant createdAt) {
        public static MediaDto from(Media m) {
            return new MediaDto(m.getId(), m.getEntityType(), m.getEntityId(), m.getUrl(),
                m.getMediaType(), m.getMimeType(), m.getSizeBytes(), m.getSortOrder(),
                m.getMetadata(), m.getCreatedAt());
        }
    }

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
                          Long sizeBytes, String originalName, Instant createdAt, UUID createdById) {
        public static FileDto from(FileAttachment f) {
            return new FileDto(f.getId(), f.getEntityType(), f.getEntityId(), f.getPath(),
                f.getMimeType(), f.getSizeBytes(), f.getOriginalName(), f.getCreatedAt(), f.getCreatedById());
        }
    }

    public record FileCreateDto(
        @NotBlank String entityType,
        @NotNull UUID entityId,
        @NotBlank String path,
        String mimeType,
        Long sizeBytes,
        String originalName
    ) {}
}
