package com.onesley.oneclick.repository.tenant;

import com.onesley.oneclick.entity.tenant.TenantAnnouncement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link TenantAnnouncement} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface TenantAnnouncementRepository extends JpaRepository<TenantAnnouncement, UUID> {
}
