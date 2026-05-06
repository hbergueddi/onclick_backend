package com.onesley.oneclick.repository.auth;

import com.onesley.oneclick.entity.auth.CustomRole;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link CustomRole} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface CustomRoleRepository extends JpaRepository<CustomRole, UUID>, JpaSpecificationExecutor<CustomRole> {
}
