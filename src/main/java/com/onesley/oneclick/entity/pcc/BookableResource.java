package com.onesley.oneclick.entity.pcc;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.shared.BookablePaymentMode;
import com.onesley.oneclick.entity.shared.BookableResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.bookable_resources} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "bookable_resources")
public class BookableResource extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "resource_type", nullable = false, columnDefinition = "bookable_resource_type")
    private BookableResourceType resourceType;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "slot_duration_minutes", nullable = false)
    private Integer slotDurationMinutes;

    @Column(name = "max_invitees", nullable = false)
    private Integer maxInvitees;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opening_hours", columnDefinition = "jsonb")
    private Map<String, Object> openingHours = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "payment_mode", nullable = false, columnDefinition = "bookable_payment_mode")
    private BookablePaymentMode paymentMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pricing", columnDefinition = "jsonb")
    private Map<String, Object> pricing = new HashMap<>();

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "image_url")
    private String imageUrl;

    protected BookableResource() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getRestaurantId() { return restaurantId; }
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
}
