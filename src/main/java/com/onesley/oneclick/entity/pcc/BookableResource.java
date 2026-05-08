package com.onesley.oneclick.entity.pcc;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.restaurant.Restaurant;
import com.onesley.oneclick.entity.shared.BookablePaymentMode;
import com.onesley.oneclick.entity.shared.BookableResourceType;
import com.onesley.oneclick.entity.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.bookable_resources} — ressources réservables PCC
 * (padel, spa, golf, séminaires, restos, coiffeur, palm gym).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code tenant_id} → {@link Tenant} en {@code @ManyToOne(LAZY)}, nullable.</li>
 *   <li>{@code restaurant_id} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, nullable
 *       (certaines ressources comme padel courts ne sont pas attachées à un resto).</li>
 * </ul>
 */
@Entity
@Table(name = "bookable_resources")
public class BookableResource extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "restaurant_id", insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @NotNull
    @Column(name = "resource_type", nullable = false, columnDefinition = "bookable_resource_type")
    private BookableResourceType resourceType;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @NotNull
    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @NotNull
    @Column(name = "slot_duration_minutes", nullable = false)
    private Integer slotDurationMinutes;

    @NotNull
    @Column(name = "max_invitees", nullable = false)
    private Integer maxInvitees;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opening_hours", columnDefinition = "jsonb")
    private Map<String, Object> openingHours = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @NotNull
    @Column(name = "payment_mode", nullable = false, columnDefinition = "bookable_payment_mode")
    private BookablePaymentMode paymentMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pricing", columnDefinition = "jsonb")
    private Map<String, Object> pricing = new HashMap<>();

    @NotNull
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "image_url")
    private String imageUrl;

    protected BookableResource() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
    public BookableResourceType getResourceType() { return resourceType; }
    public String getName() { return name; }
    public Integer getCapacity() { return capacity; }
    public Integer getSlotDurationMinutes() { return slotDurationMinutes; }
    public Integer getMaxInvitees() { return maxInvitees; }
    public Map<String, Object> getOpeningHours() { return openingHours; }
    public BookablePaymentMode getPaymentMode() { return paymentMode; }
    public Map<String, Object> getPricing() { return pricing; }
    public Boolean getEnabled() { return enabled; }
    public String getImageUrl() { return imageUrl; }

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
        BookableResource that = (BookableResource) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
