package com.onesley.oneclick.core.configuration;

import com.onesley.oneclick.cache.CacheConfig;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.core.configuration.ConfigurationDtos.*;

@Service
@Transactional(readOnly = true)
public class ConfigurationService {

    private final FeatureFlagRepository flagRepo;
    private final FeatureFlagTargetRepository targetRepo;
    private final CacheConfigurationRepository cacheRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public ConfigurationService(FeatureFlagRepository flagRepo,
                                FeatureFlagTargetRepository targetRepo,
                                CacheConfigurationRepository cacheRepo) {
        this.flagRepo = flagRepo;
        this.targetRepo = targetRepo;
        this.cacheRepo = cacheRepo;
    }

    // ─── Feature flags ───────────────────────────────────────────────────────

    public List<FeatureFlagDto> findAllFlags() {
        return flagRepo.findAll().stream().map(FeatureFlagDto::from).toList();
    }

    @Cacheable(value = CacheConfig.CACHE_FEATURE_FLAGS, key = "#code")
    public FeatureFlagDto findFlagByCode(String code) {
        return FeatureFlagDto.from(flagRepo.findByCode(code)
            .orElseThrow(() -> new NotFoundException("FeatureFlag by code: " + code)));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_FEATURE_FLAGS, allEntries = true)
    public FeatureFlagDto createFlag(FeatureFlagCreateDto dto) {
        if (flagRepo.findByCode(dto.code()).isPresent()) {
            throw new ConflictException("Code feature flag déjà utilisé : " + dto.code());
        }
        FeatureFlag f = new FeatureFlag(UUID.randomUUID(), dto.code(), dto.name());
        if (dto.description() != null) f.setDescription(dto.description());
        if (dto.enabled() != null)     f.setEnabled(dto.enabled());
        if (dto.rolloutPct() != null)  f.setRolloutPct(dto.rolloutPct());
        return FeatureFlagDto.from(flagRepo.save(f));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_FEATURE_FLAGS, allEntries = true)
    public FeatureFlagDto updateFlag(UUID id, FeatureFlagUpdateDto dto) {
        FeatureFlag f = flagRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("FeatureFlag", id));
        if (dto.enabled() != null)     f.setEnabled(dto.enabled());
        if (dto.rolloutPct() != null)  f.setRolloutPct(dto.rolloutPct());
        if (dto.description() != null) f.setDescription(dto.description());
        return FeatureFlagDto.from(flagRepo.save(f));
    }

    // ─── Feature flag targets ────────────────────────────────────────────────

    public List<FeatureFlagTargetDto> findTargetsByFlag(UUID featureFlagId) {
        return targetRepo.findAllByFeatureFlagId(featureFlagId).stream()
            .map(FeatureFlagTargetDto::from)
            .toList();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_FEATURE_FLAGS, allEntries = true)
    public FeatureFlagTargetDto createTarget(FeatureFlagTargetCreateDto dto) {
        FeatureFlag flagRef = entityManager.getReference(FeatureFlag.class, dto.featureFlagId());
        boolean enabled = dto.enabled() == null || dto.enabled();
        FeatureFlagTarget t = new FeatureFlagTarget(UUID.randomUUID(), flagRef, dto.targetType(),
            dto.targetId(), enabled);
        return FeatureFlagTargetDto.from(targetRepo.save(t));
    }

    // ─── Cache configurations ────────────────────────────────────────────────

    public List<CacheConfigDto> findAllCacheConfigs() {
        return cacheRepo.findAll().stream().map(CacheConfigDto::from).toList();
    }

    @Transactional
    public CacheConfigDto createCacheConfig(CacheConfigCreateDto dto) {
        if (cacheRepo.findByCacheName(dto.cacheName()).isPresent()) {
            throw new ConflictException("Cache déjà configuré : " + dto.cacheName());
        }
        CacheConfiguration c = new CacheConfiguration(UUID.randomUUID(), dto.cacheName(), dto.ttlSeconds());
        if (dto.maxEntries() != null) c.setMaxEntries(dto.maxEntries());
        return CacheConfigDto.from(cacheRepo.save(c));
    }
}
