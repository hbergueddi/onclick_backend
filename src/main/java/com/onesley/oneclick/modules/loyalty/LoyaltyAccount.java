package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.User;
import com.onesley.oneclick.modules.restaurant.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @Column(name = "client_id", nullable = false, insertable = false, updatable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "tier_id", insertable = false, updatable = false)
    private UUID tierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id")
    private Tier tier;

    @Min(0)
    @Column(name = "balance", nullable = false)
    private Integer balance = 0;

    protected LoyaltyAccount() {
        // JPA
    }

    public LoyaltyAccount(UUID id, User client, Restaurant restaurant) {
        this.id = id;
        this.client = client;
        this.restaurant = restaurant;
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public User getClient() { return client; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public UUID getTierId() { return tierId; }
    public Tier getTier() { return tier; }
    public void setTier(Tier tier) { this.tier = tier; }
    public Integer getBalance() { return balance; }
    public void setBalance(Integer balance) { this.balance = balance; }
    public void addPoints(int points) { this.balance = Math.max(0, this.balance + points); }
    public void deductPoints(int points) { this.balance = Math.max(0, this.balance - points); }

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
