package com.onesley.oneclick.entity.admin;

import com.onesley.oneclick.entity.restaurant.Restaurant;
import com.onesley.oneclick.audit.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.quota_change_logs} — audit des modifications de quotas
 * services restaurant.
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code restaurant_id NOT NULL} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code changed_by} : audit field, reste UUID brut (changed_by_name dénormalisé à côté).</li>
 * </ul>
 */
@Entity
@Table(name = "quota_change_logs")
public class QuotaChangeLog extends CreatedAtEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @NotBlank
    @Column(name = "service_type", nullable = false)
    private String serviceType;

    @NotBlank
    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @NotNull
    @Column(name = "old_quota", nullable = false)
    private Integer oldQuota;

    @NotNull
    @Column(name = "new_quota", nullable = false)
    private Integer newQuota;

    /** Audit field : UUID brut. */
    @NotNull
    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @NotBlank
    @Column(name = "changed_by_name", nullable = false)
    private String changedByName;

    @NotBlank
    @Column(name = "change_source", nullable = false)
    private String changeSource;

    protected QuotaChangeLog() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
    public String getServiceType() { return serviceType; }
    public String getServiceName() { return serviceName; }
    public Integer getOldQuota() { return oldQuota; }
    public Integer getNewQuota() { return newQuota; }
    public UUID getChangedBy() { return changedBy; }
    public String getChangedByName() { return changedByName; }
    public String getChangeSource() { return changeSource; }

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
        QuotaChangeLog that = (QuotaChangeLog) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
