package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.core.identity.internal.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Invité d'une réservation — soit un user OneClick existant (guest_user_id),
 * soit un nom libre (guest_name) — au moins un des deux.
 */
@Entity
@Table(name = "reservation_guests")
@EntityListeners(AuditingEntityListener.class)
public class ReservationGuest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reservation_id", nullable = false, insertable = false, updatable = false)
    private UUID reservationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

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

    protected ReservationGuest() {
        // JPA
    }

    public ReservationGuest(UUID id, Reservation reservation, User guestUser, String guestName) {
        this.id = id;
        this.reservation = reservation;
        this.guestUser = guestUser;
        this.guestName = guestName;
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public Reservation getReservation() { return reservation; }
    public UUID getGuestUserId() { return guestUserId; }
    public User getGuestUser() { return guestUser; }
    public String getGuestName() { return guestName; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        ReservationGuest that = (ReservationGuest) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
