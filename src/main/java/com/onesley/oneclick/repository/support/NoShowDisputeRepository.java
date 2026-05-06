package com.onesley.oneclick.repository.support;

import com.onesley.oneclick.entity.support.NoShowDispute;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link NoShowDispute} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface NoShowDisputeRepository extends JpaRepository<NoShowDispute, UUID>, JpaSpecificationExecutor<NoShowDispute> {
}
