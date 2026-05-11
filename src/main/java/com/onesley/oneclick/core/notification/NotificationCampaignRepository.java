package com.onesley.oneclick.core.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link NotificationCampaign} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface NotificationCampaignRepository extends JpaRepository<NotificationCampaign, UUID>, JpaSpecificationExecutor<NotificationCampaign> {
    java.util.List<NotificationCampaign> findAllByTenantId(java.util.UUID tenantId);
    java.util.List<NotificationCampaign> findAllByCreatedById(java.util.UUID createdById);
}
