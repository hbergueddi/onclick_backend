package com.onesley.oneclick.repository.tenant;

import com.onesley.oneclick.entity.tenant.AnnouncementRead;
import com.onesley.oneclick.entity.tenant.AnnouncementReadId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link AnnouncementRead} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, AnnouncementReadId>, JpaSpecificationExecutor<AnnouncementRead> {
}
