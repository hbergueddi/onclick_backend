package com.onesley.oneclick.core.configuration.internal;

import com.onesley.oneclick.core.configuration.api.ConfigurationDtos.CacheConfigCreateDto;
import com.onesley.oneclick.core.configuration.api.ConfigurationDtos.FeatureFlagCreateDto;
import com.onesley.oneclick.core.configuration.api.ConfigurationDtos.FeatureFlagTargetCreateDto;
import com.onesley.oneclick.core.configuration.api.ConfigurationDtos.FeatureFlagUpdateDto;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link ConfigurationService} (L3 — core.configuration).
 * Feature flags (CRUD + conflit code), targets (défaut enabled), cache configs.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class ConfigurationServiceTest {

    @Mock FeatureFlagRepository flagRepo;
    @Mock FeatureFlagTargetRepository targetRepo;
    @Mock CacheConfigurationRepository cacheRepo;
    @Mock EntityManager entityManager;
    @InjectMocks ConfigurationService service;

    @BeforeEach
    void injectEm() { ReflectionTestUtils.setField(service, "entityManager", entityManager); }

    private FeatureFlag flag() { return new FeatureFlag(UUID.randomUUID(), "new_ui", "Nouvelle UI"); }

    // ─── feature flags ─────────────────────────────────────────────────────────

    @Test
    void findAllFlags_maps() {
        when(flagRepo.findAll()).thenReturn(List.of(flag()));
        assertThat(service.findAllFlags()).hasSize(1);
    }

    @Test
    void findFlagByCode_notFoundAndFound() {
        when(flagRepo.findByCode("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findFlagByCode("missing")).isInstanceOf(NotFoundException.class);
        when(flagRepo.findByCode("new_ui")).thenReturn(Optional.of(flag()));
        assertThat(service.findFlagByCode("new_ui")).isNotNull();
    }

    @Test
    void createFlag_conflict_whenCodeExists() {
        when(flagRepo.findByCode("new_ui")).thenReturn(Optional.of(flag()));
        assertThatThrownBy(() -> service.createFlag(new FeatureFlagCreateDto("new_ui", "X", null, null, null)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void createFlag_success_withOptionalFields() {
        when(flagRepo.findByCode("new_ui")).thenReturn(Optional.empty());
        when(flagRepo.save(any(FeatureFlag.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.createFlag(new FeatureFlagCreateDto("new_ui", "Nouvelle UI", "desc", true, 50))).isNotNull();
        assertThat(service.createFlag(new FeatureFlagCreateDto("new_ui", "Nouvelle UI", null, null, null))).isNotNull();
    }

    @Test
    void updateFlag_notFound_throwsNotFound() {
        when(flagRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateFlag(UUID.randomUUID(), new FeatureFlagUpdateDto(true, 10, "d")))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateFlag_success_appliesNonNullFields() {
        FeatureFlag f = flag();
        when(flagRepo.findById(any())).thenReturn(Optional.of(f));
        when(flagRepo.save(any(FeatureFlag.class))).thenAnswer(i -> i.getArgument(0));
        service.updateFlag(f.getId(), new FeatureFlagUpdateDto(true, 75, "maj"));
        assertThat(f.getRolloutPct()).isEqualTo(75);
    }

    // ─── targets ───────────────────────────────────────────────────────────────

    @Test
    void findTargetsByFlag_maps() {
        UUID fid = UUID.randomUUID();
        when(targetRepo.findAllByFeatureFlagId(fid)).thenReturn(List.of(
            new FeatureFlagTarget(UUID.randomUUID(), flag(), "user", UUID.randomUUID(), true)));
        assertThat(service.findTargetsByFlag(fid)).hasSize(1);
    }

    @Test
    void createTarget_enabledDefaultsTrue_andExplicit() {
        when(entityManager.getReference(eq(FeatureFlag.class), any())).thenReturn(flag());
        when(targetRepo.save(any(FeatureFlagTarget.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.createTarget(new FeatureFlagTargetCreateDto(UUID.randomUUID(), "user", UUID.randomUUID(), null))).isNotNull();
        assertThat(service.createTarget(new FeatureFlagTargetCreateDto(UUID.randomUUID(), "tenant", UUID.randomUUID(), false))).isNotNull();
    }

    // ─── cache configs ───────────────────────────────────────────────────────────

    @Test
    void findAllCacheConfigs_maps() {
        when(cacheRepo.findAll()).thenReturn(List.of(new CacheConfiguration(UUID.randomUUID(), "userDetails", 3600)));
        assertThat(service.findAllCacheConfigs()).hasSize(1);
    }

    @Test
    void createCacheConfig_conflict_whenNameExists() {
        when(cacheRepo.findByCacheName("userDetails"))
            .thenReturn(Optional.of(new CacheConfiguration(UUID.randomUUID(), "userDetails", 3600)));
        assertThatThrownBy(() -> service.createCacheConfig(new CacheConfigCreateDto("userDetails", 3600, 1000)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void createCacheConfig_success_withAndWithoutMaxEntries() {
        when(cacheRepo.findByCacheName(any())).thenReturn(Optional.empty());
        when(cacheRepo.save(any(CacheConfiguration.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.createCacheConfig(new CacheConfigCreateDto("c1", 3600, 5000))).isNotNull();
        assertThat(service.createCacheConfig(new CacheConfigCreateDto("c2", 60, null))).isNotNull();
    }
}
