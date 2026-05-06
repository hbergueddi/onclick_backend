package com.onesley.oneclick.repository.admin;

import com.onesley.oneclick.entity.admin.DeviceToken;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link DeviceToken} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {
}
