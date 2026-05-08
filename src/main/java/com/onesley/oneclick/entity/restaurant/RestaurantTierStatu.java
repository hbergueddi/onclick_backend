package com.onesley.oneclick.entity.restaurant;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.restaurant_tier_status} — statut tier courant d'un restaurant
 * (Essentiel / Performance / Signature) calculé sur le CA mensuel + grace period.
 *
 * <h3>Pattern : 1-1 owner avec {@link Restaurant}</h3>
 *
 * <p>UNIQUE INDEX en DB sur {@code restaurant_id}
 * ({@code restaurant_tier_status_restaurant_id_key}) confirmé en passe 3.
 * Donc {@code @OneToOne} (pas {@code @ManyToOne}) côté propriétaire :
 * <ul>
 *   <li>PK = {@code id} séparée (pas de shared PK comme TenantBranding).</li>
 *   <li>FK unique sur {@code restaurant_id}.</li>
 *   <li>Inverse côté Restaurant : {@code @OneToOne(mappedBy="restaurant") tierStatus}.</li>
 * </ul>
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code restaurant_id NOT NULL UNIQUE} → {@link Restaurant} en {@code @OneToOne(LAZY)}, optional=false.</li>
 *   <li>{@code current_tier_id} → reste UUID brut (validé avec senior : {@code current_tier_slug}
 *       est dénormalisé à côté → la jointure vers TierThreshold serait redondante).</li>
 * </ul>
 */
@Entity
@Table(name = "restaurant_tier_status")
public class RestaurantTierStatu extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // ─── Jointure 1-1 restaurant_id (UNIQUE en DB) ──────────────────────────
    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false, unique = true)
    private Restaurant restaurant;

    /** UUID brut conservé : {@code current_tier_slug} ci-dessous est dénormalisé. */
    @Column(name = "current_tier_id")
    private UUID currentTierId;

    @NotBlank
    @Column(name = "current_tier_slug", nullable = false)
    private String currentTierSlug;

    @NotNull
    @Column(name = "monthly_ca", nullable = false)
    private BigDecimal monthlyCa;

    @Column(name = "tier_achieved_at")
    private Instant tierAchievedAt;

    @Column(name = "grace_expires_at")
    private Instant graceExpiresAt;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    protected RestaurantTierStatu() {
        // JPA
    }

    public UUID getId() { return id; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getRestaurantId() { return restaurantId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }

    public UUID getCurrentTierId() { return currentTierId; }
    public String getCurrentTierSlug() { return currentTierSlug; }
    public BigDecimal getMonthlyCa() { return monthlyCa; }
    public Instant getTierAchievedAt() { return tierAchievedAt; }
    public Instant getGraceExpiresAt() { return graceExpiresAt; }
    public Instant getLastEvaluatedAt() { return lastEvaluatedAt; }

    // ─── equals / hashCode anti-proxy LAZY ──────────────────────────────────

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
        RestaurantTierStatu that = (RestaurantTierStatu) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
