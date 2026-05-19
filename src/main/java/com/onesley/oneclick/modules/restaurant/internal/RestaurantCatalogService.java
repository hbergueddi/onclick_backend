package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.cache.CacheConfig;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import com.onesley.oneclick.modules.restaurant.api.RestaurantCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantPatchDto;
import lombok.RequiredArgsConstructor;

/**
 * Service {@link Restaurant} — CRUD + filtres par ville/tenant/status.
 *
 * <p>Renommé en {@code RestaurantCatalogService} (vs RestaurantService) pour
 * éviter la collision avec l'entité JPA {@code RestaurantService} (qui désigne
 * un service du restaurant, ex: brunch/dîner — voir {@link MealService}).
 *
 * <p>Soft delete filtré via Specification dans toutes les méthodes de lecture.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RestaurantCatalogService {

    private final RestaurantRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public Page<RestaurantDto> findAll(String city, UUID tenantId, int page, int size) {
        Specification<Restaurant> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (city != null && !city.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("city"), city));
        }
        if (tenantId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("tenantId"), tenantId));
        }
        return repository.findAll(spec, PageRequest.of(page, size, Sort.by("name"))).map(Restaurant::toDto);
    }

    @Cacheable(value = CacheConfig.CACHE_RESTAURANTS, key = "#id")
    public RestaurantDto findById(UUID id) {
        Restaurant r = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Restaurant", id));
        return r.toDto();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_RESTAURANTS, allEntries = true)
    public RestaurantDto create(RestaurantCreateDto dto) {
        Tenant tenantRef = entityManager.getReference(Tenant.class, dto.tenantId());
        Restaurant r = new Restaurant(UUID.randomUUID(), tenantRef, dto.name(), dto.city());
        r.setDescription(dto.description());
        r.setPhone(dto.phone());
        r.setAddress(dto.address());
        r.setLatitude(dto.latitude());
        r.setLongitude(dto.longitude());
        r.setCuisine(dto.cuisine());
        r.setMaxStaff(dto.maxStaff());
        r.setGroupId(dto.groupId());
        return repository.save(r).toDto();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_RESTAURANTS, key = "#id")
    public void softDelete(UUID id) {
        Restaurant r = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Restaurant", id));
        r.markDeleted();
        repository.save(r);
    }

    /**
     * Patch partiel d'un Restaurant — Sprint G.2.2.
     *
     * <p>Tous les champs DTO sont optionnels (PATCH semantics). Seuls les champs
     * non-null sont appliqués. Pour tags (liste), {@code null} = pas de modif,
     * liste vide = effacement de tous les tags.
     *
     * <p>Eviction cache CACHE_RESTAURANTS car la version du DTO change.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_RESTAURANTS, key = "#id")
    public RestaurantDto patch(UUID id, RestaurantPatchDto dto) {
        Restaurant r = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Restaurant", id));

        if (dto.name() != null)        r.setName(dto.name());
        if (dto.description() != null) r.setDescription(dto.description());
        if (dto.phone() != null)       r.setPhone(dto.phone());
        if (dto.address() != null)     r.setAddress(dto.address());
        if (dto.city() != null)        r.setCity(dto.city());
        if (dto.latitude() != null)    r.setLatitude(dto.latitude());
        if (dto.longitude() != null)   r.setLongitude(dto.longitude());
        if (dto.status() != null)      r.setStatus(dto.status());
        if (dto.budget() != null)      r.setBudget(dto.budget());
        if (dto.tags() != null)        r.setTags(dto.tags().toArray(new String[0]));
        if (dto.loungePts() != null)   r.setLoungePts(dto.loungePts());
        if (dto.image() != null)       r.setImage(dto.image());
        if (dto.cuisine() != null)     r.setCuisine(dto.cuisine());
        if (dto.maxStaff() != null)    r.setMaxStaff(dto.maxStaff());
        if (dto.groupId() != null)     r.setGroupId(dto.groupId());

        return repository.save(r).toDto();
    }
}
