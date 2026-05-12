package com.onesley.oneclick.modules.promotion.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.modules.promotion.api.OfferDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Offre / promotion par restaurant. */
@Entity
@Table(name = "offers")
public class Offer extends SoftDeletableAuditedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "restaurant_id", nullable = false) private UUID restaurantId;
    @NotBlank @Column(name = "title", nullable = false) private String title;
    @Column(name = "description") private String description;
    @NotNull @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @NotNull @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @DecimalMin("0.00") @DecimalMax("100.00") @Column(name = "discount_pct", precision = 5, scale = 2) private BigDecimal discountPct;
    @Column(name = "discount_amount", precision = 12, scale = 2) private BigDecimal discountAmount;
    @Column(name = "enabled", nullable = false) private boolean enabled = true;

    protected Offer() {}
    public Offer(UUID id, UUID restaurantId, String title, Instant startsAt, Instant expiresAt) {
        this.id = id; this.restaurantId = restaurantId; this.title = title; this.startsAt = startsAt; this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public BigDecimal getDiscountPct() { return discountPct; }
    public void setDiscountPct(BigDecimal discountPct) { this.discountPct = discountPct; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** Mapping vers le DTO public exposé hors du module. */
    public OfferDto toDto() {
        return new OfferDto(id, restaurantId, title, description, startsAt, expiresAt,
            discountPct, discountAmount, enabled, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((Offer) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
