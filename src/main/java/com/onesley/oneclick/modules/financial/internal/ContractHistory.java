package com.onesley.oneclick.modules.financial.internal;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Journal d'audit d'une modification de champ de contrat (V59).
 *
 * <p>Une ligne = un champ suivi qui a changé lors d'un PATCH {@code /api/financial/contracts/{id}}.
 * Alimenté best-effort par {@code FinancialService.updateContract}. Lu par le panneau admin
 * ContractHistoryPanel (timeline ancienne → nouvelle valeur).
 */
@Entity
@Table(name = "contract_history")
@Getter
public class ContractHistory {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "contract_id", nullable = false)
    @Setter private UUID contractId;

    @Column(name = "field_name", nullable = false, length = 64)
    @Setter private String fieldName;

    @Column(name = "old_value", length = 256)
    @Setter private String oldValue;

    @Column(name = "new_value", length = 256)
    @Setter private String newValue;

    @Column(name = "changed_by")
    @Setter private UUID changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractHistoryDto toDto() {
        return new com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractHistoryDto(
            id, contractId, fieldName, oldValue, newValue, changedBy, createdAt);
    }
}
