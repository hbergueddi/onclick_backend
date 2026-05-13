package com.onesley.oneclick.modules.event.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.api.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;
import com.onesley.oneclick.modules.event.api.Event;
import com.onesley.oneclick.modules.event.api.EventDtos.ParticipationDto;

/** RSVP sur un Event. */
@Entity
@Table(name = "event_participations", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "user_id"}))
public class EventParticipation extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "event_id", nullable = false, insertable = false, updatable = false) private UUID eventId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "event_id", nullable = false) private Event event;
    @Column(name = "user_id", nullable = false, insertable = false, updatable = false) private UUID userId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Pattern(regexp = "^(going|maybe|declined|attended)$") @Column(name = "status", nullable = false) private String status = "going";

    // V20 — Sprint D : Elite +1 invité
    @Column(name = "plus_one_name") private String plusOneName;

    protected EventParticipation() {}
    public EventParticipation(UUID id, Event event, User user, String status) {
        this.id = id; this.event = event; this.user = user; this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public Event getEvent() { return event; }
    public UUID getUserId() { return userId; }
    public User getUser() { return user; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPlusOneName() { return plusOneName; }
    public void setPlusOneName(String plusOneName) { this.plusOneName = plusOneName; }

    /** Mapping vers le DTO public exposé hors du module. */
    public ParticipationDto toDto() {
        return new ParticipationDto(id, eventId, userId, status, plusOneName, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((EventParticipation) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
