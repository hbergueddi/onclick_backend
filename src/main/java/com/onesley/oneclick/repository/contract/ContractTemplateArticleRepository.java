package com.onesley.oneclick.repository.contract;

import com.onesley.oneclick.entity.contract.ContractTemplateArticle;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link ContractTemplateArticle} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface ContractTemplateArticleRepository extends JpaRepository<ContractTemplateArticle, UUID> {
}
