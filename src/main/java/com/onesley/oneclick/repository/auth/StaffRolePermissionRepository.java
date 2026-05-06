package com.onesley.oneclick.repository.auth;

import com.onesley.oneclick.entity.auth.StaffRolePermission;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link StaffRolePermission} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface StaffRolePermissionRepository extends JpaRepository<StaffRolePermission, UUID> {
}
