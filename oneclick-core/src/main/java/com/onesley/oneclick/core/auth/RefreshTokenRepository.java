package com.onesley.oneclick.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link RefreshToken} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID>, JpaSpecificationExecutor<RefreshToken> {
    java.util.List<RefreshToken> findAllByUserId(java.util.UUID userId);
    java.util.Optional<RefreshToken> findByToken(String token);
}
