package com.onesley.oneclick.entity.contract;

import com.onesley.oneclick.audit.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.contract_history} — log des modifications d'un contrat
 * (audit field-level, append-only).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code contract_id NOT NULL} → {@link PartnerContract} en {@code @ManyToOne(LAZY)}, optional=false.
 *       Côté inverse : {@link PartnerContract#getHistory()} cascade {PERSIST, MERGE} (append-only).</li>
 *   <li>{@code changed_by} : audit field, reste UUID brut.</li>
 * </ul>
 */
@Entity
@Table(name = "contract_history")
public class ContractHistory extends CreatedAtEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "contract_id", nullable = false, insertable = false, updatable = false)
    private UUID contractId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private PartnerContract contract;

    @NotBlank
    @Column(name = "field_changed", nullable = false)
    private String fieldChanged;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    /** Audit field : UUID brut. */
    @Column(name = "changed_by")
    private UUID changedBy;

    protected ContractHistory() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public PartnerContract getContract() { return contract; }
    /** Package-private : appelé par les helpers du parent {@link PartnerContract#addHistory}. */
    void setContract(PartnerContract contract) { this.contract = contract; }
    public String getFieldChanged() { return fieldChanged; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public UUID getChangedBy() { return changedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        ContractHistory that = (ContractHistory) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
