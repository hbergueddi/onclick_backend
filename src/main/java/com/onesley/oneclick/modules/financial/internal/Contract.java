package com.onesley.oneclick.modules.financial.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.modules.restaurant.internal.Restaurant;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Contrat partenaire — commission_rate par restaurant. */
@Entity
@Table(name = "contracts")
public class Contract extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @NotBlank
    @Column(name = "contract_number", nullable = false, unique = true)
    private String contractNumber;

    @NotNull
    @DecimalMin("0.00") @DecimalMax("100.00")
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @NotNull
    @Column(name = "starts_at", nullable = false)
    private LocalDate startsAt;

    @Column(name = "ends_at")
    private LocalDate endsAt;

    @Pattern(regexp = "^(draft|active|paused|terminated)$")
    @Column(name = "status", nullable = false)
    private String status = "active";

    protected Contract() {}

    public Contract(UUID id, Restaurant restaurant, String contractNumber, BigDecimal commissionRate, LocalDate startsAt) {
        this.id = id;
        this.restaurant = restaurant;
        this.contractNumber = contractNumber;
        this.commissionRate = commissionRate;
        this.startsAt = startsAt;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public String getContractNumber() { return contractNumber; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public void setCommissionRate(BigDecimal commissionRate) { this.commissionRate = commissionRate; }
    public LocalDate getStartsAt() { return startsAt; }
    public LocalDate getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDate endsAt) { this.endsAt = endsAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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
