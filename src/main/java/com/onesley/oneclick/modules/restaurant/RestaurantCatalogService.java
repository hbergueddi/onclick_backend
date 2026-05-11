package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.core.tenant.Tenant;
import com.onesley.oneclick.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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
public class RestaurantCatalogService {

    private final RestaurantRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public RestaurantCatalogService(RestaurantRepository repository) {
        this.repository = repository;
    }

    public Page<RestaurantDto> findAll(String city, UUID tenantId, int page, int size) {
        Specification<Restaurant> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (city != null && !city.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("city"), city));
        }
        if (tenantId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("tenantId"), tenantId));
        }
        return repository.findAll(spec, PageRequest.of(page, size, Sort.by("name"))).map(RestaurantDto::from);
    }

    public RestaurantDto findById(UUID id) {
        Restaurant r = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Restaurant", id));
        return RestaurantDto.from(r);
    }

    @Transactional
    public RestaurantDto create(RestaurantCreateDto dto) {
        Tenant tenantRef = entityManager.getReference(Tenant.class, dto.tenantId());
        Restaurant r = new Restaurant(UUID.randomUUID(), tenantRef, dto.name(), dto.city());
        r.setDescription(dto.description());
        r.setPhone(dto.phone());
        r.setAddress(dto.address());
        r.setLatitude(dto.latitude());
        r.setLongitude(dto.longitude());
        return RestaurantDto.from(repository.save(r));
    }

    @Transactional
    public void softDelete(UUID id) {
        Restaurant r = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Restaurant", id));
        r.markDeleted();
        repository.save(r);
    }
}
