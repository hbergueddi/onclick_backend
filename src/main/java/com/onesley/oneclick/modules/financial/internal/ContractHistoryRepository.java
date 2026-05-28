package com.onesley.oneclick.modules.financial.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContractHistoryRepository extends JpaRepository<ContractHistory, UUID> {
    List<ContractHistory> findByContractIdOrderByCreatedAtDesc(UUID contractId);
}
