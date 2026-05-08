package com.onesley.oneclick.entity.admin;

import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.restaurant.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.admin_wallet_transactions} — transactions sur wallet admin
 * (distributions vers restaurants, expiration, etc.).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code admin_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code restaurant_id} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, nullable.</li>
 * </ul>
 */
@Entity
@Table(name = "admin_wallet_transactions")
@EntityListeners(AuditingEntityListener.class)
public class AdminWalletTransaction {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "admin_id", nullable = false, insertable = false, updatable = false)
    private UUID adminId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Profile admin;

    @Column(name = "restaurant_id", insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @NotNull
    @Column(name = "amount", nullable = false)
    private Integer amount;

    @NotBlank
    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "details")
    private String details;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @NotNull
    @Column(name = "remaining_amount", nullable = false)
    private Integer remainingAmount;

    protected AdminWalletTransaction() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getAdminId() { return adminId; }
    public Profile getAdmin() { return admin; }
    public void setAdmin(Profile admin) { this.admin = admin; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
    public Integer getAmount() { return amount; }
    public String getReason() { return reason; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Integer getRemainingAmount() { return remainingAmount; }

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
        AdminWalletTransaction that = (AdminWalletTransaction) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
