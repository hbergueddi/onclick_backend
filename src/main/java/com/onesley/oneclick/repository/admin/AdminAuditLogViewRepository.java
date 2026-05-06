package com.onesley.oneclick.repository.admin;

import com.onesley.oneclick.entity.admin.AdminAuditLogView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Repository read-only pour {@link AdminAuditLogView} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
public interface AdminAuditLogViewRepository extends Repository<AdminAuditLogView, UUID> {

    Optional<AdminAuditLogView> findById(UUID id);

    List<AdminAuditLogView> findAll();

    long count();
}
