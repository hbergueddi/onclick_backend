package com.onesley.oneclick.core.configuration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link CacheConfiguration} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface CacheConfigurationRepository extends JpaRepository<CacheConfiguration, UUID>, JpaSpecificationExecutor<CacheConfiguration> {
    java.util.Optional<CacheConfiguration> findByCacheName(String cacheName);
}
