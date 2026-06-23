package com.onesley.oneclick.core.tenant.internal;

import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.core.tenant.api.TenantDirectoryApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Implémentation du port {@link TenantDirectoryApi} (lecture seule de l'annuaire tenants).
 *
 * <p>Soft-delete : on filtre {@code deleted_at IS NULL} (le finder dérivé {@code findBySlug} ne le
 * fait pas — cf. doc {@code TenantRepository}).
 */
@Service
public class TenantDirectoryService implements TenantDirectoryApi {

    private final TenantRepository repository;

    public TenantDirectoryService(TenantRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findIdBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return repository.findBySlug(slug.trim())
                .filter(t -> t.getDeletedAt() == null)
                .map(Tenant::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> slugById(UUID tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return repository.findById(tenantId)
                .filter(t -> t.getDeletedAt() == null)
                .map(Tenant::getSlug);
    }
}
