package com.onesley.oneclick.modules.financial.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Repository des articles désactivés par contrat (V56). */
@Repository
public interface ContractDisabledArticleRepository extends JpaRepository<ContractDisabledArticle, UUID> {
    List<ContractDisabledArticle> findAllByContractId(UUID contractId);
    void deleteByContractId(UUID contractId);
}
