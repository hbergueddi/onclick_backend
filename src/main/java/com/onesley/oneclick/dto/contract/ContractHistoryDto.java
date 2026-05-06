package com.onesley.oneclick.dto.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code contract_history} (généré par scripts/scaffold-jpa.mjs).
 */
public record ContractHistoryDto(
    UUID id,
    UUID contractId,
    String fieldChanged,
    String oldValue,
    String newValue,
    UUID changedBy,
    Instant createdAt
) {
}
