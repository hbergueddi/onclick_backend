package com.onesley.oneclick.repository.support;

import com.onesley.oneclick.entity.support.RuleTemplate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link RuleTemplate} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface RuleTemplateRepository extends JpaRepository<RuleTemplate, UUID> {
}
