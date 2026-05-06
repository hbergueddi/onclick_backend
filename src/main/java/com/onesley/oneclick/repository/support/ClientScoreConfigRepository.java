package com.onesley.oneclick.repository.support;

import com.onesley.oneclick.entity.support.ClientScoreConfig;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link ClientScoreConfig} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface ClientScoreConfigRepository extends JpaRepository<ClientScoreConfig, UUID>, JpaSpecificationExecutor<ClientScoreConfig> {
}
