package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.marketing.Offer;
import com.onesley.oneclick.entity.restaurant.Restaurant;
import com.onesley.oneclick.entity.shared.DistributionSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.point_distributions} — distributions de points par l'admin
 * (segments client, bonus système, ou points liés à une offre).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code admin_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code restaurant_id} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, nullable.</li>
 *   <li>{@code client_id} → {@link Profile} en {@code @ManyToOne(LAZY)}, nullable
 *       (segment-based : null si distribution massive sur un segment).</li>
 *   <li>{@code offer_id} → {@link Offer} en {@code @ManyToOne(LAZY)}, nullable.</li>
 *   <li>{@code campaign_id} → reste UUID brut (entité {@code Campaign} non scaffold,
 *       à valider en V2).</li>
 * </ul>
 */
@Entity
@Table(name = "point_distributions")
@EntityListeners(AuditingEntityListener.class)
public class PointDistribution {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "admin_id", nullable = false, insertable = false, updatable = false)
    private UUID adminId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Profile admin;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @NotNull
    @Column(name = "source_type", nullable = false, columnDefinition = "distribution_source")
    private DistributionSource sourceType;

    @Column(name = "restaurant_id", insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @Column(name = "client_id", insertable = false, updatable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Profile client;

    @Column(name = "segment")
    private String segment;

    @NotNull
    @Column(name = "points", nullable = false)
    private Integer points;

    @NotBlank
    @Column(name = "reason", nullable = false)
    private String reason;

    /** UUID brut conservé : entité Campaign non scaffold. */
    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "offer_id", insertable = false, updatable = false)
    private UUID offerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id")
    private Offer offer;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PointDistribution() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getAdminId() { return adminId; }
    public Profile getAdmin() { return admin; }
    public void setAdmin(Profile admin) { this.admin = admin; }
    public DistributionSource getSourceType() { return sourceType; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
    public UUID getClientId() { return clientId; }
    public Profile getClient() { return client; }
    public void setClient(Profile client) { this.client = client; }
    public String getSegment() { return segment; }
    public Integer getPoints() { return points; }
    public String getReason() { return reason; }
    public UUID getCampaignId() { return campaignId; }
    public UUID getOfferId() { return offerId; }
    public Offer getOffer() { return offer; }
    public void setOffer(Offer offer) { this.offer = offer; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

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
        PointDistribution that = (PointDistribution) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
