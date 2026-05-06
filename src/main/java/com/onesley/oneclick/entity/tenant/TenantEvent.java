package com.onesley.oneclick.entity.tenant;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.tenant_events} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "tenant_events")
public class TenantEvent extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "category")
    private String category;

    @Column(name = "event_date", nullable = false)
    private Instant eventDate;

    @Column(name = "event_end_date")
    private Instant eventEndDate;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "rsvp_enabled", nullable = false)
    private Boolean rsvpEnabled;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "visible_until")
    private Instant visibleUntil;

    protected TenantEvent() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getPhotoUrl() { return photoUrl; }
    public String getCategory() { return category; }
    public Instant getEventDate() { return eventDate; }
    public Instant getEventEndDate() { return eventEndDate; }
    public Integer getCapacity() { return capacity; }
    public Boolean getRsvpEnabled() { return rsvpEnabled; }
    public String getStatus() { return status; }
    public Integer getDisplayOrder() { return displayOrder; }
    public Instant getVisibleUntil() { return visibleUntil; }
}
