package com.onesley.oneclick.core.configuration.api;

import jakarta.validation.constraints.Max;
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
        @NotBlank String code,
        @NotBlank String name,
        String description,
        Boolean enabled,
        @Min(0) @Max(100) Integer rolloutPct
    ) {}

    public record FeatureFlagUpdateDto(
        Boolean enabled,
        @Min(0) @Max(100) Integer rolloutPct,
        String description
    ) {}

    // ─── FeatureFlagTarget ───────────────────────────────────────────────────

    public record FeatureFlagTargetDto(UUID id, UUID featureFlagId, String targetType, UUID targetId,
                                       boolean enabled, Instant createdAt) {}

    public record FeatureFlagTargetCreateDto(
        @NotNull UUID featureFlagId,
        @NotNull @Pattern(regexp = "^(user|tenant|role)$") String targetType,
        @NotNull UUID targetId,
        Boolean enabled
    ) {}

    // ─── CacheConfiguration ──────────────────────────────────────────────────

    public record CacheConfigDto(UUID id, String cacheName, Integer ttlSeconds, Integer maxEntries,
                                 boolean enabled, Instant createdAt) {}

    public record CacheConfigCreateDto(
        @NotBlank String cacheName,
        @NotNull @Min(1) Integer ttlSeconds,
        Integer maxEntries
    ) {}
}
