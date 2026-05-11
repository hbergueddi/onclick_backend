package com.onesley.oneclick.core.configuration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.core.configuration.ConfigurationDtos.*;

@RestController
@RequestMapping("/api/configuration")
@Tag(name = "Configuration", description = "Feature flags (rollout progressif) + cache configurations (§19)")
public class ConfigurationController {

    private final ConfigurationService service;

    public ConfigurationController(ConfigurationService service) {
        this.service = service;
    }

    // ─── Feature flags ───────────────────────────────────────────────────────

    @GetMapping("/feature-flags")
    @Operation(summary = "Tous les feature flags (admin)")
    public List<FeatureFlagDto> findAllFlags() { return service.findAllFlags(); }

    @GetMapping("/feature-flags/by-code/{code}")
    @Operation(summary = "Lookup feature flag par code — utilisé par les services pour gate une feature.")
    public FeatureFlagDto findFlagByCode(@PathVariable String code) {
        return service.findFlagByCode(code);
    }

    @PostMapping("/feature-flags")
    public ResponseEntity<FeatureFlagDto> createFlag(@Valid @RequestBody FeatureFlagCreateDto dto) {
        FeatureFlagDto f = service.createFlag(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(f);
    }

    @PatchMapping("/feature-flags/{id}")
    @Operation(summary = "Mise à jour enabled / rolloutPct / description (cache évicté)")
    public FeatureFlagDto updateFlag(@PathVariable UUID id, @Valid @RequestBody FeatureFlagUpdateDto dto) {
        return service.updateFlag(id, dto);
    }

    // ─── Feature flag targets ────────────────────────────────────────────────

    @GetMapping("/feature-flags/{featureFlagId}/targets")
    @Operation(summary = "Overrides spécifiques d'un flag (par user/tenant/role)")
    public List<FeatureFlagTargetDto> findTargetsByFlag(@PathVariable UUID featureFlagId) {
        return service.findTargetsByFlag(featureFlagId);
    }

    @PostMapping("/feature-flag-targets")
    public ResponseEntity<FeatureFlagTargetDto> createTarget(@Valid @RequestBody FeatureFlagTargetCreateDto dto) {
        FeatureFlagTargetDto t = service.createTarget(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(t);
    }

    // ─── Cache configurations ────────────────────────────────────────────────

    @GetMapping("/cache-configs")
    @Operation(summary = "TTL et taille max des caches (vue admin — modifiable sans redéploiement)")
    public List<CacheConfigDto> findAllCacheConfigs() { return service.findAllCacheConfigs(); }

    @PostMapping("/cache-configs")
    public ResponseEntity<CacheConfigDto> createCacheConfig(@Valid @RequestBody CacheConfigCreateDto dto) {
        CacheConfigDto c = service.createCacheConfig(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(c);
    }
}
