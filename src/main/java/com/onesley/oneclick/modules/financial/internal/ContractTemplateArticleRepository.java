package com.onesley.oneclick.modules.financial.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Repository des articles (clauses) de template contractuel (V55). */
@Repository
public interface ContractTemplateArticleRepository extends JpaRepository<ContractTemplateArticle, UUID> {
    List<ContractTemplateArticle> findAllByTemplateIdOrderBySortOrderAscArticleNumberAsc(UUID templateId);
}
