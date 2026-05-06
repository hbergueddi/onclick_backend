package com.onesley.oneclick.entity.pcc;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Entité {@code public.event_rsvps} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "event_rsvps")
public class EventRsvp extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "status", nullable = false)
    private String status;

    protected EventRsvp() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public UUID getUserId() { return userId; }
    public String getStatus() { return status; }
}
