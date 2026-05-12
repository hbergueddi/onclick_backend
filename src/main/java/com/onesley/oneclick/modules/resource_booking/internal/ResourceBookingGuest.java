package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.*;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Invités d'une réservation de ressource. */
@Entity
@Table(name = "resource_booking_guests")
@EntityListeners(AuditingEntityListener.class)
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
    private String guestName;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected ResourceBookingGuest() {}

    public ResourceBookingGuest(UUID id, ResourceBooking booking, User guestUser, String guestName) {
        this.id = id;
        this.booking = booking;
        this.guestUser = guestUser;
        this.guestName = guestName;
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public ResourceBooking getBooking() { return booking; }
    public UUID getGuestUserId() { return guestUserId; }
    public User getGuestUser() { return guestUser; }
    public String getGuestName() { return guestName; }
    public Instant getCreatedAt() { return createdAt; }

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
