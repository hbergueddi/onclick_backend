package com.onesley.oneclick.core.audit_log.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import com.onesley.oneclick.core.audit_log.api.SystemEvent;

/**
 * Repository {@link SystemEvent} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface SystemEventRepository extends JpaRepository<SystemEvent, UUID>, JpaSpecificationExecutor<SystemEvent> {}
