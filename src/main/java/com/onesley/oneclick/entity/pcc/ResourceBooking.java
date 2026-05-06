package com.onesley.oneclick.entity.pcc;

import com.onesley.oneclick.audit.AuditedEntity;
import com.onesley.oneclick.entity.shared.ResourceBookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.resource_bookings} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : audit niveau 1 (4 colonnes).
 */
@Entity
@Table(name = "resource_bookings")
public class ResourceBooking extends AuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @NotNull
    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @NotNull
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @NotNull
    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @NotNull
    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    @JdbcTypeCode(SqlTypes.JSON)
    @NotNull
    @Column(name = "invitees", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> invitees = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @NotNull
    @Column(name = "status", nullable = false, columnDefinition = "resource_booking_status")
    private ResourceBookingStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pricing_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> pricingSnapshot = new HashMap<>();

    @Column(name = "notes")
    private String notes;

    @Column(name = "reminder_j1_sent_at")
    private Instant reminderJ1SentAt;

    @Column(name = "reminder_h2_sent_at")
    private Instant reminderH2SentAt;

    protected ResourceBooking() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getResourceId() { return resourceId; }
    public UUID getOrganizerId() { return organizerId; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public Integer getPartySize() { return partySize; }
    public Map<String, Object> getInvitees() { return invitees; }
    public ResourceBookingStatus getStatus() { return status; }
    public Map<String, Object> getPricingSnapshot() { return pricingSnapshot; }
    public String getNotes() { return notes; }
    public Instant getReminderJ1SentAt() { return reminderJ1SentAt; }
    public Instant getReminderH2SentAt() { return reminderH2SentAt; }
}
