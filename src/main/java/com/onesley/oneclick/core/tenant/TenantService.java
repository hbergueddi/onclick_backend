package com.onesley.oneclick.core.tenant;

import com.onesley.oneclick.cache.CacheConfig;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TenantService {

    private final TenantRepository repository;

    public TenantService(TenantRepository repository) {
        this.repository = repository;
    }

    public List<TenantDto> findAll() {
        return repository.findAll().stream()
            .filter(t -> t.getDeletedAt() == null)
            .map(TenantDto::from)
            .toList();
    }

    public TenantDto findById(UUID id) {
        Tenant t = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Tenant", id));
        return TenantDto.from(t);
    }

    @Cacheable(value = CacheConfig.CACHE_TENANTS_BY_SLUG, key = "#slug")
    public TenantDto findBySlug(String slug) {
        Tenant t = repository.findBySlug(slug)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Tenant by slug: " + slug));
        return TenantDto.from(t);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_TENANTS_BY_SLUG, allEntries = true)
    public TenantDto create(TenantCreateDto dto) {
        if (repository.findBySlug(dto.slug()).isPresent()) {
            throw new ConflictException("Slug déjà utilisé : " + dto.slug());
        }
        Tenant t = new Tenant(UUID.randomUUID(), dto.name(), dto.slug());
        return TenantDto.from(repository.save(t));
    }
}
