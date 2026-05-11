package com.onesley.oneclick.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link DeviceToken} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID>, JpaSpecificationExecutor<DeviceToken> {
    java.util.List<DeviceToken> findAllByUserId(java.util.UUID userId);
    java.util.Optional<DeviceToken> findByToken(String token);
}
