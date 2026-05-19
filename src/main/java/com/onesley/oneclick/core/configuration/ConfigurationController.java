package com.onesley.oneclick.core.configuration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.core.configuration.api.ConfigurationDtos.*;
import com.onesley.oneclick.core.configuration.api.ConfigurationDtos;
import com.onesley.oneclick.core.configuration.api.ConfigurationDtos.CacheConfigCreateDto;
import com.onesley.oneclick.core.configuration.api.ConfigurationDtos.CacheConfigDto;
import com.onesley.oneclick.core.configuration.api.ConfigurationDtos.FeatureFlagCreateDto;
import com.onesley.oneclick.core.configuration.api.ConfigurationDtos.FeatureFlagDto;
import com.onesley.oneclick.core.configuration.api.ConfigurationDtos.FeatureFlagTargetCreateDto;
import com.onesley.oneclick.core.configuration.api.ConfigurationDtos.FeatureFlagTargetDto;
import com.onesley.oneclick.core.configuration.api.ConfigurationDtos.FeatureFlagUpdateDto;
import com.onesley.oneclick.core.configuration.internal.ConfigurationService;
import lombok.RequiredArgsConstructor;

/**
 * Bug 32 (Batch D RBAC v2) — RESOURCE=AUDIT (configs admin/monitoring).
 */
@RestController
@RequestMapping("/api/configuration")
@Tag(name = "Configuration", description = "Feature flags (rollout progressif) + cache configurations (§19)")
@RequiredArgsConstructor
public class ConfigurationController {

    private final ConfigurationService service;

    // ─── Feature flags ───────────────────────────────────────────────────────

    @GetMapping("/feature-flags")
    @Operation(summary = "Tous les feature flags (admin)")
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
    public List<FeatureFlagDto> findAllFlags() { return service.findAllFlags(); }

    @GetMapping("/feature-flags/by-code/{code}")
    @Operation(summary = "Lookup feature flag par code — utilisé par les services pour gate une feature.")
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
    public FeatureFlagDto findFlagByCode(@PathVariable String code) {
        return service.findFlagByCode(code);
    }

    @PostMapping("/feature-flags")
    @PreAuthorize("hasAuthority('CREATE:AUDIT')")
    public ResponseEntity<FeatureFlagDto> createFlag(@Valid @RequestBody FeatureFlagCreateDto dto) {
        FeatureFlagDto f = service.createFlag(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(f);
    }

    @PatchMapping("/feature-flags/{id}")
    @Operation(summary = "Mise à jour enabled / rolloutPct / description (cache évicté)")
    @PreAuthorize("hasAuthority('UPDATE:AUDIT')")
    public FeatureFlagDto updateFlag(@PathVariable UUID id, @Valid @RequestBody FeatureFlagUpdateDto dto) {
        return service.updateFlag(id, dto);
    }

    // ─── Feature flag targets ────────────────────────────────────────────────

    @GetMapping("/feature-flags/{featureFlagId}/targets")
    @Operation(summary = "Overrides spécifiques d'un flag (par user/tenant/role)")
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
    public List<FeatureFlagTargetDto> findTargetsByFlag(@PathVariable UUID featureFlagId) {
        return service.findTargetsByFlag(featureFlagId);
    }

    @PostMapping("/feature-flag-targets")
    @PreAuthorize("hasAuthority('CREATE:AUDIT')")
    public ResponseEntity<FeatureFlagTargetDto> createTarget(@Valid @RequestBody FeatureFlagTargetCreateDto dto) {
        FeatureFlagTargetDto t = service.createTarget(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(t);
    }

    // ─── Cache configurations ────────────────────────────────────────────────

    @GetMapping("/cache-configs")
    @Operation(summary = "TTL et taille max des caches (vue admin — modifiable sans redéploiement)")
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
    public List<CacheConfigDto> findAllCacheConfigs() { return service.findAllCacheConfigs(); }

    @PostMapping("/cache-configs")
    @PreAuthorize("hasAuthority('CREATE:AUDIT')")
    public ResponseEntity<CacheConfigDto> createCacheConfig(@Valid @RequestBody CacheConfigCreateDto dto) {
        CacheConfigDto c = service.createCacheConfig(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(c);
    }
}
