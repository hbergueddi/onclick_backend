package com.onesley.oneclick.entity.restaurant;

import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.audit.CreatedAtEntity;
import com.onesley.oneclick.entity.loyalty.ExpiredPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.restaurant_expired_pool} — pool de points expirés
 * recyclés vers les restaurants (50% workflow expiration FIFO 90j).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code restaurant_id NOT NULL} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code client_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code expired_point_id NOT NULL} → {@link ExpiredPoint} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code restitution_id} → {@link RestaurantRestitution} en {@code @ManyToOne(LAZY)},
 *       nullable (les pools en attente de consolidation).</li>
 * </ul>
 */
@Entity
@Table(name = "restaurant_expired_pool")
public class RestaurantExpiredPool extends CreatedAtEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // ─── Jointure restaurant_id ─────────────────────────────────────────────
    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    // ─── Jointure client_id ─────────────────────────────────────────────────
    @Column(name = "client_id", nullable = false, insertable = false, updatable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Profile client;

    // ─── Jointure expired_point_id ──────────────────────────────────────────
    @Column(name = "expired_point_id", nullable = false, insertable = false, updatable = false)
    private UUID expiredPointId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expired_point_id", nullable = false)
    private ExpiredPoint expiredPoint;

    @NotNull
    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "consolidated_at")
    private Instant consolidatedAt;

    // ─── Jointure restitution_id (nullable, attente consolidation) ──────────
    @Column(name = "restitution_id", insertable = false, updatable = false)
    private UUID restitutionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restitution_id")
    private RestaurantRestitution restitution;

    protected RestaurantExpiredPool() {
        // JPA
    }

    public UUID getId() { return id; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getRestaurantId() { return restaurantId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getClientId() { return clientId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Profile getClient() { return client; }
    public void setClient(Profile client) { this.client = client; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getExpiredPointId() { return expiredPointId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public ExpiredPoint getExpiredPoint() { return expiredPoint; }
    public void setExpiredPoint(ExpiredPoint expiredPoint) { this.expiredPoint = expiredPoint; }

    public Integer getPoints() { return points; }
    public Instant getConsolidatedAt() { return consolidatedAt; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getRestitutionId() { return restitutionId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public RestaurantRestitution getRestitution() { return restitution; }
    public void setRestitution(RestaurantRestitution restitution) { this.restitution = restitution; }

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
        RestaurantExpiredPool that = (RestaurantExpiredPool) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
