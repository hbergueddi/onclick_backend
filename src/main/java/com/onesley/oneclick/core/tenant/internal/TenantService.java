package com.onesley.oneclick.core.tenant.internal;

import com.onesley.oneclick.cache.CacheConfig;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.onesley.oneclick.core.tenant.api.TenantCreateDto;
import com.onesley.oneclick.core.tenant.api.TenantDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantBrandingDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantBrandingUpdateDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantFeatureDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminDtos.TenantUpdateDto;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository repository;
    private final TenantBrandingRepository brandingRepository;
    private final TenantFeatureRepository featureRepository;

    private static final Set<String> VALID_STATUSES = Set.of("active", "paused", "archived");

    public List<TenantDto> findAll() {
        return repository.findAll().stream()
            .filter(t -> t.getDeletedAt() == null)
            .map(Tenant::toDto)
            .toList();
    }

    public TenantDto findById(UUID id) {
        Tenant t = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Tenant", id));
        return t.toDto();
    }

    @Cacheable(value = CacheConfig.CACHE_TENANTS_BY_SLUG, key = "#slug")
    public TenantDto findBySlug(String slug) {
        Tenant t = repository.findBySlug(slug)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Tenant by slug: " + slug));
        return t.toDto();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_TENANTS_BY_SLUG, allEntries = true)
    public TenantDto create(TenantCreateDto dto) {
        if (repository.findBySlug(dto.slug()).isPresent()) {
            throw new ConflictException("Slug déjà utilisé : " + dto.slug());
        }
        Tenant t = new Tenant(UUID.randomUUID(), dto.name(), dto.slug());
        return repository.save(t).toDto();
    }

    // ─── C1 portail tenant-admin (SUPERADMIN-only) ───────────────────────────────

    /** Met à jour nom/statut d'un tenant (slug immuable). Évince le cache slug. */
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_TENANTS_BY_SLUG, allEntries = true)
    public TenantDto updateTenant(UUID id, TenantUpdateDto dto) {
        Tenant t = requireTenant(id);
        if (dto.name() != null && !dto.name().isBlank()) {
            t.setName(dto.name().trim());
        }
        if (dto.status() != null) {
            if (!VALID_STATUSES.contains(dto.status())) {
                throw new BadRequestException("Statut invalide (active|paused|archived)");
            }
            t.setStatus(dto.status());
        }
        return repository.save(t).toDto();
    }

    /** Branding d'un tenant ; retourne un branding vide (tenantId + nulls) si pas encore défini. */
    public TenantBrandingDto getBranding(UUID id) {
        requireTenant(id);
        return brandingRepository.findById(id)
            .map(b -> new TenantBrandingDto(b.getTenantId(), b.getLogoUrl(), b.getPrimaryColor(),
                b.getAccentColor(), b.getCustomDomain()))
            .orElse(new TenantBrandingDto(id, null, null, null, null));
    }

    /** Remplace le branding (upsert sur la PK tenant_id @MapsId). */
    @Transactional
    public TenantBrandingDto updateBranding(UUID id, TenantBrandingUpdateDto dto) {
        Tenant t = requireTenant(id);
        TenantBranding b = brandingRepository.findById(id).orElseGet(() -> new TenantBranding(t));
        b.setLogoUrl(trimToNull(dto.logoUrl()));
        b.setPrimaryColor(trimToNull(dto.primaryColor()));
        b.setAccentColor(trimToNull(dto.accentColor()));
        b.setCustomDomain(trimToNull(dto.customDomain()));
        TenantBranding saved = brandingRepository.save(b);
        return new TenantBrandingDto(saved.getTenantId(), saved.getLogoUrl(), saved.getPrimaryColor(),
            saved.getAccentColor(), saved.getCustomDomain());
    }

    /** Liste des feature flags d'un tenant. */
    public List<TenantFeatureDto> listFeatures(UUID id) {
        requireTenant(id);
        return featureRepository.findAllByTenantId(id).stream()
            .map(f -> new TenantFeatureDto(f.getFeatureCode(), f.isEnabled()))
            .toList();
    }

    /** Active/désactive un feature flag (upsert sur le couple unique (tenant_id, feature_code)). */
    @Transactional
    public TenantFeatureDto toggleFeature(UUID id, String featureCode, boolean enabled) {
        Tenant t = requireTenant(id);
        String code = featureCode == null ? null : featureCode.trim();
        if (code == null || code.isEmpty()) {
            throw new BadRequestException("feature_code requis");
        }
        TenantFeature existing = featureRepository.findAllByTenantId(id).stream()
            .filter(f -> code.equals(f.getFeatureCode()))
            .findFirst()
            .orElse(null);
        TenantFeature saved;
        if (existing != null) {
            existing.setEnabled(enabled);
            saved = featureRepository.save(existing);
        } else {
            saved = featureRepository.save(new TenantFeature(UUID.randomUUID(), t, code, enabled));
        }
        return new TenantFeatureDto(saved.getFeatureCode(), saved.isEnabled());
    }

    private Tenant requireTenant(UUID id) {
        return repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Tenant", id));
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String v = s.trim();
        return v.isEmpty() ? null : v;
    }
}
