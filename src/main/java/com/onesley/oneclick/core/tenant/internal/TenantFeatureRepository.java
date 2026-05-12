package com.onesley.oneclick.core.tenant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link TenantFeature} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface TenantFeatureRepository extends JpaRepository<TenantFeature, UUID>, JpaSpecificationExecutor<TenantFeature> {
    java.util.List<TenantFeature> findAllByTenantId(java.util.UUID tenantId);
}
