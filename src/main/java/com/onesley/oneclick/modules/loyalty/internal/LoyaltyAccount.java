package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyAccountDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

/**
 * Compte fidélité — 1 par couple (client × restaurant).
 *
 * <p>{@code balance} est dénormalisé (recalculé via SUM(loyalty_transactions.points))
 * pour les performances de lecture. La consistance est garantie par les services
 * qui font tjs INSERT transaction + UPDATE balance dans la même transaction JPA.
 */
@Entity
@Table(
    name = "loyalty_accounts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "restaurant_id"})
)
public class LoyaltyAccount extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "tier_id")
    private UUID tierId;

    // tenant_id rempli automatiquement par trigger DB V10 (depuis restaurants.tenant_id)
    // → read-only côté Hibernate
    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @Min(0)
    @Column(name = "balance", nullable = false)
    private Integer balance = 0;

    protected LoyaltyAccount() {
        // JPA
    }

    public LoyaltyAccount(UUID id, UUID clientId, UUID restaurantId) {
        this.id = id;
        this.clientId = clientId;
        this.restaurantId = restaurantId;
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getTierId() { return tierId; }
    public void setTierId(UUID tierId) { this.tierId = tierId; }
    public UUID getTenantId() { return tenantId; }
    public Integer getBalance() { return balance; }
    public void setBalance(Integer balance) { this.balance = balance; }
    public void addPoints(int points) { this.balance = Math.max(0, this.balance + points); }
    public void deductPoints(int points) { this.balance = Math.max(0, this.balance - points); }

    /** Mapping vers le DTO public exposé hors du module. */
    public LoyaltyAccountDto toDto() {
        return new LoyaltyAccountDto(id, clientId, restaurantId, tierId, balance, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        LoyaltyAccount that = (LoyaltyAccount) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
