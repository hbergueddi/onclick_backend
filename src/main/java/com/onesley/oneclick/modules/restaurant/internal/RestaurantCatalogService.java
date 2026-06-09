package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.cache.CacheConfig;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.TenantScope;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
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
    private final LifecycleEventService lifecycleEventService;
    private final TenantScope tenantScope;

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
        // Périmètre tenant (fuite de périmètre) : un client ne voit que {tenant public} ∪ ses
        // memberships ; SUPERADMIN (null) → aucun filtre. Catalogue PUBLIC : non authentifié → public seul.
        Set<UUID> visible = tenantScope.visibleTenantIdsOrNull();
        if (visible != null) {
            spec = spec.and((root, q, cb) -> root.get("tenantId").in(visible));
        }
        return repository.findAll(spec, PageRequest.of(page, size, Sort.by("name"))).map(Restaurant::toDto);
    }

    // Pas de @Cacheable : le contrôle de périmètre (canSeeTenant) dépend du caller — un résultat mis
    // en cache par id serait renvoyé sans re-vérification à un autre caller (contournement de portée).
    public RestaurantDto findById(UUID id) {
        Restaurant r = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Restaurant", id));
        // Hors périmètre : 404 (ne pas divulguer l'existence d'un restaurant d'un programme non accessible).
        if (!tenantScope.canSeeTenant(r.getTenantId())) {
            throw new NotFoundException("Restaurant", id);
        }
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
        Restaurant saved = repository.save(r);
        // Producteur lifecycle_events (V45) : inscription d'un nouveau restaurant.
        lifecycleEventService.record("inscription", saved.getId(),
            "Inscription du restaurant « " + saved.getName() + " » (" + saved.getCity() + ")");
        return saved.toDto();
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

        String oldStatus = r.getStatus();

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

        RestaurantDto result = repository.save(r).toDto();

        // Producteur lifecycle_events (V45). Une transition de statut prime sur une
        // simple modification de fiche (événement le plus signifiant). Un PATCH no-op
        // (aucun champ, ou statut inchangé sans autre champ) ne journalise rien.
        boolean statusChanged = dto.status() != null && !dto.status().equals(oldStatus);
        if (statusChanged) {
            lifecycleEventService.record(statusEvent(dto.status()), id,
                "Changement de statut : " + oldStatus + " → " + dto.status());
        } else if (hasEditableFieldChange(dto)) {
            lifecycleEventService.record("modification", id, "Mise à jour de la fiche restaurant");
        }

        return result;
    }

    /**
     * E1 — marque l'onboarding self-service du restaurant comme terminé (timestamp serveur).
     * Idempotent (ré-appel = re-stampe). Dirty-checking persiste via save().
     */
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_RESTAURANTS, key = "#id")
    public RestaurantDto markOnboardingComplete(UUID id) {
        Restaurant r = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Restaurant", id));
        r.setOnboardingCompletedAt(java.time.Instant.now());
        return repository.save(r).toDto();
    }

    /**
     * Mappe le nouveau statut canonique EN ({@code active|paused|archived}) vers la
     * clé d'événement FR attendue par la page admin CycleDeVie ({@code eventConfig}).
     *
     * <p>Le modèle de statut restaurant n'a que 3 états : un passage à {@code active}
     * est toujours une <em>réactivation</em> (l'activation initiale est journalisée
     * comme « inscription » à la création, pas via un PATCH). Tout statut hors
     * vocabulaire retombe sur « modification » (sûreté).
     */
    static String statusEvent(String newStatus) {
        if (newStatus == null) return "modification";
        return switch (newStatus) {
            case "paused"   -> "suspension";
            case "active"   -> "réactivation";
            case "archived" -> "rejet";
            default          -> "modification";
        };
    }

    /** Vrai si le PATCH modifie au moins un champ non-statut (→ événement « modification »). */
    static boolean hasEditableFieldChange(RestaurantPatchDto dto) {
        return dto.name() != null || dto.description() != null || dto.phone() != null
            || dto.address() != null || dto.city() != null || dto.latitude() != null
            || dto.longitude() != null || dto.budget() != null || dto.tags() != null
            || dto.loungePts() != null || dto.image() != null || dto.cuisine() != null
            || dto.maxStaff() != null || dto.groupId() != null;
    }
}
