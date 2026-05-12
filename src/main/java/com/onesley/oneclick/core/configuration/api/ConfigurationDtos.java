package com.onesley.oneclick.core.configuration.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;
import com.onesley.oneclick.core.configuration.internal.CacheConfiguration;
import com.onesley.oneclick.core.configuration.internal.FeatureFlag;
import com.onesley.oneclick.core.configuration.internal.FeatureFlagTarget;

public final class ConfigurationDtos {

    private ConfigurationDtos() {}

    // ─── FeatureFlag ─────────────────────────────────────────────────────────

    public record FeatureFlagDto(UUID id, String code, String name, String description, boolean enabled,
                                 Integer rolloutPct, Instant createdAt) {
        public static FeatureFlagDto from(FeatureFlag f) {
            return new FeatureFlagDto(f.getId(), f.getCode(), f.getName(), f.getDescription(),
                f.isEnabled(), f.getRolloutPct(), f.getCreatedAt());
        }
    }

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
                                       boolean enabled, Instant createdAt) {
        public static FeatureFlagTargetDto from(FeatureFlagTarget t) {
            return new FeatureFlagTargetDto(t.getId(), t.getFeatureFlagId(), t.getTargetType(),
                t.getTargetId(), t.isEnabled(), t.getCreatedAt());
        }
    }

    public record FeatureFlagTargetCreateDto(
        @NotNull UUID featureFlagId,
        @NotNull @Pattern(regexp = "^(user|tenant|role)$") String targetType,
        @NotNull UUID targetId,
        Boolean enabled
    ) {}

    // ─── CacheConfiguration ──────────────────────────────────────────────────

    public record CacheConfigDto(UUID id, String cacheName, Integer ttlSeconds, Integer maxEntries,
                                 boolean enabled, Instant createdAt) {
        public static CacheConfigDto from(CacheConfiguration c) {
            return new CacheConfigDto(c.getId(), c.getCacheName(), c.getTtlSeconds(),
                c.getMaxEntries(), c.isEnabled(), c.getCreatedAt());
        }
    }

    public record CacheConfigCreateDto(
        @NotBlank String cacheName,
        @NotNull @Min(1) Integer ttlSeconds,
        Integer maxEntries
    ) {}
}
