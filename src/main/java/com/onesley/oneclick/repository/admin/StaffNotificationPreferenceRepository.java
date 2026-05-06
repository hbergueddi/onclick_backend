package com.onesley.oneclick.repository.admin;

import com.onesley.oneclick.entity.admin.StaffNotificationPreference;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link StaffNotificationPreference} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface StaffNotificationPreferenceRepository extends JpaRepository<StaffNotificationPreference, UUID>, JpaSpecificationExecutor<StaffNotificationPreference> {
}
