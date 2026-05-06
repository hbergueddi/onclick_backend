package com.onesley.oneclick.entity.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.contract_history} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "contract_history")
@EntityListeners(AuditingEntityListener.class)
public class ContractHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "field_changed", nullable = false)
    private String fieldChanged;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "changed_by")
    private UUID changedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ContractHistory() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public String getFieldChanged() { return fieldChanged; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public UUID getChangedBy() { return changedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
