package com.onesley.oneclick.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link LoginHistory} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID>, JpaSpecificationExecutor<LoginHistory> {
    java.util.List<LoginHistory> findAllByUserId(java.util.UUID userId);
}
