package com.onesley.oneclick.core.configuration.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs publics du module configuration.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Entity.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 */
public final class ConfigurationDtos {

    private ConfigurationDtos() {}

    // ─── FeatureFlag ─────────────────────────────────────────────────────────

    public record FeatureFlagDto(UUID id, String code, String name, String description, boolean enabled,
                                 Integer rolloutPct, Instant createdAt) {}

    public record FeatureFlagCreateDto(
        @NotBlank @Size(min = 1, max = 64) String code,
        @NotBlank @Size(min = 1, max = 128) String name,
        @Size(min = 1, max = 1024) String description,
        Boolean enabled,
        @Min(0) @Max(100) Integer rolloutPct
    ) {}

    public record FeatureFlagUpdateDto(
        Boolean enabled,
        @Min(0) @Max(100) Integer rolloutPct,
        @Size(min = 1, max = 1024) String description
    ) {}

    // ─── FeatureFlagTarget ───────────────────────────────────────────────────

    public record FeatureFlagTargetDto(UUID id, UUID featureFlagId, String targetType, UUID targetId,
                                       boolean enabled, Instant createdAt) {}

    public record FeatureFlagTargetCreateDto(
        @NotNull UUID featureFlagId,
        @NotNull @Pattern(regexp = "^(user|tenant|role)$") @Size(min = 1, max = 64) String targetType,
        @NotNull UUID targetId,
        Boolean enabled
    ) {}

    // ─── CacheConfiguration ──────────────────────────────────────────────────

    public record CacheConfigDto(UUID id, String cacheName, Integer ttlSeconds, Integer maxEntries,
                                 boolean enabled, Instant createdAt) {}

    public record CacheConfigCreateDto(
        @NotBlank @Size(min = 1, max = 128) String cacheName,
        @NotNull @Min(1) Integer ttlSeconds,
        Integer maxEntries
    ) {}
}
