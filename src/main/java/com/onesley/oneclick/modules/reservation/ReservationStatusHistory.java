package com.onesley.oneclick.modules.reservation;

import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit workflow d'une réservation — 1 ligne par changement de statut.
 *
 * <p>Permet de retracer qui a changé le statut, quand, et avec quel motif
 * (raison de refus, motif d'annulation, etc.).
 */
@Entity
@Table(name = "reservation_status_histories")
public class ReservationStatusHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reservation_id", nullable = false, insertable = false, updatable = false)
    private UUID reservationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "old_status")
    private String oldStatus;

    @NotBlank
    @Column(name = "new_status", nullable = false)
    private String newStatus;

    @Column(name = "changed_by", insertable = false, updatable = false)
    private UUID changedById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(name = "reason")
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();

    protected ReservationStatusHistory() {
        // JPA
    }

    public ReservationStatusHistory(UUID id, Reservation reservation, String oldStatus, String newStatus, User changedBy) {
        this.id = id;
        this.reservation = reservation;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public Reservation getReservation() { return reservation; }
    public String getOldStatus() { return oldStatus; }
    public String getNewStatus() { return newStatus; }
    public UUID getChangedById() { return changedById; }
    public User getChangedBy() { return changedBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getChangedAt() { return changedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        ReservationStatusHistory that = (ReservationStatusHistory) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
