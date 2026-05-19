package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.BookingDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Réservation d'une ressource (vs réservation restaurant). */
@Entity
@Table(name = "resource_bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceBooking extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "resource_id", nullable = false, insertable = false, updatable = false)
    private UUID resourceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @Column(name = "organizer_id", nullable = false, insertable = false, updatable = false)
    private UUID organizerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    @Column(name = "pricing_id", insertable = false, updatable = false)
    private UUID pricingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_id")
    @Setter private ResourcePricing pricing;

    @NotNull
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @NotNull
    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Pattern(regexp = "^(pending|confirmed|cancelled|no_show|completed)$")
    @Column(name = "status", nullable = false)
    @Setter private String status = "confirmed";

    @Column(name = "notes")
    @Setter private String notes;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public ResourceBooking(UUID id, Resource resource, User organizer, Instant startAt, Instant endAt) {
        this.id = id;
        this.resource = resource;
        this.organizer = organizer;
        this.startAt = startAt;
        this.endAt = endAt;
    }
    public boolean isDeleted() { return deletedAt != null; }
    public void markDeleted() { this.deletedAt = Instant.now(); }

    /** Mapping vers le DTO public exposé hors du module. */
    public BookingDto toDto() {
        return new BookingDto(id, resourceId, organizerId, pricingId, startAt, endAt, status, notes, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((ResourceBooking) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
