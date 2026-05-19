package com.onesley.oneclick.modules.financial.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Size;

/** Contrat partenaire — commission_rate par restaurant. */
@Entity
@Table(name = "contracts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contract extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @NotBlank
    @Size(max = 255)
    @Column(name = "contract_number", nullable = false, unique = true)
    private String contractNumber;

    @NotNull
    @DecimalMin("0.00") @DecimalMax("100.00")
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    @Setter private BigDecimal commissionRate;

    @NotNull
    @Column(name = "starts_at", nullable = false)
    private LocalDate startsAt;

    @Column(name = "ends_at")
    @Setter private LocalDate endsAt;

    @NotBlank
    @Pattern(regexp = "^(draft|active|paused|terminated)$")
    @Column(name = "status", nullable = false)
    @Setter @Size(max = 255) private String status = "active";

    public Contract(UUID id, UUID restaurantId, String contractNumber, BigDecimal commissionRate, LocalDate startsAt) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.contractNumber = contractNumber;
        this.commissionRate = commissionRate;
        this.startsAt = startsAt;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public ContractDto toDto() {
        return new ContractDto(id, restaurantId, contractNumber, commissionRate, startsAt, endsAt, status, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((Contract) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
