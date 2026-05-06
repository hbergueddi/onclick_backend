package com.onesley.oneclick.repository.pcc;

import com.onesley.oneclick.entity.pcc.ResourceBooking;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link ResourceBooking} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface ResourceBookingRepository extends JpaRepository<ResourceBooking, UUID>, JpaSpecificationExecutor<ResourceBooking> {
}
