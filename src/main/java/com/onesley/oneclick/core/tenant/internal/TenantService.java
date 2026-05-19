package com.onesley.oneclick.core.tenant.internal;

import com.onesley.oneclick.cache.CacheConfig;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import com.onesley.oneclick.core.tenant.api.TenantCreateDto;
import com.onesley.oneclick.core.tenant.api.TenantDto;
import com.onesley.oneclick.core.tenant.api.Tenant;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository repository;

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
}
