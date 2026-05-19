package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.GuestDto;
import jakarta.persistence.*;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Size;

/** Invités d'une réservation de ressource. */
@Entity
@Table(name = "resource_booking_guests")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceBookingGuest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false, insertable = false, updatable = false)
    private UUID bookingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private ResourceBooking booking;

    @Column(name = "guest_user_id", insertable = false, updatable = false)
    private UUID guestUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_user_id")
    private User guestUser;

    @Column(name = "guest_name")
    @Size(max = 255) private String guestName;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public ResourceBookingGuest(UUID id, ResourceBooking booking, User guestUser, String guestName) {
        this.id = id;
        this.booking = booking;
        this.guestUser = guestUser;
        this.guestName = guestName;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public GuestDto toDto() {
        return new GuestDto(id, bookingId, guestUserId, guestName, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((ResourceBookingGuest) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
