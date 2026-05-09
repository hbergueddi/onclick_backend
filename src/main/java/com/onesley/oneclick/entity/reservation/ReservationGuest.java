package com.onesley.oneclick.entity.reservation;

import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.status.EntityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.reservation_guests} — invités d'une réservation
 * (max couverts-1, l'organisateur compte pour 1 couvert).
 *
 * <h3>Jointures JPA (passe 3) — aggregate member de {@link Reservation}</h3>
 * <ul>
 *   <li>{@code reservation_id NOT NULL} → {@link Reservation} en {@code @ManyToOne(LAZY)}, optional=false.
 *       Côté inverse : {@link Reservation#getGuests()} cascade ALL + orphanRemoval.</li>
 *   <li>{@code invited_by NOT NULL} → {@link Profile} (l'organisateur qui invite) en
 *       {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code guest_user_id} → {@link Profile} en {@code @ManyToOne(LAZY)}, nullable
 *       (auto-link via phone si l'invité a un compte ; sinon le guest reste tagué
 *       par {@code guest_phone} + {@code guest_name} sans compte).</li>
 * </ul>
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

    @Column(name = "invited_by", nullable = false, insertable = false, updatable = false)
    private UUID invitedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by", nullable = false)
    private Profile inviter;

    @Column(name = "guest_name")
    private String guestName;

    @NotBlank
    @Column(name = "guest_phone", nullable = false)
    private String guestPhone;

    @Column(name = "guest_user_id", insertable = false, updatable = false)
    private UUID guestUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_user_id")
    private Profile guestUser;

    @Column(name = "status_id", nullable = false, insertable = false, updatable = false)
    private UUID statusId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private EntityStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "seen_by_host", nullable = false)
    private Boolean seenByHost;

    protected ReservationGuest() {
        // JPA
    }

    public UUID getId() { return id; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getReservationId() { return reservationId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Reservation getReservation() { return reservation; }
    /** Package-private : appelé par les helpers {@link Reservation#addGuest}/{@code removeGuest}. */
    void setReservation(Reservation reservation) { this.reservation = reservation; }

    public UUID getInvitedBy() { return invitedBy; }
    public Profile getInviter() { return inviter; }
    public void setInviter(Profile inviter) { this.inviter = inviter; }

    public String getGuestName() { return guestName; }
    public String getGuestPhone() { return guestPhone; }

    public UUID getGuestUserId() { return guestUserId; }
    public Profile getGuestUser() { return guestUser; }
    public void setGuestUser(Profile guestUser) { this.guestUser = guestUser; }

    public UUID getStatusId() { return statusId; }
    public EntityStatus getStatus() { return status; }
    public void setStatus(EntityStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Boolean getSeenByHost() { return seenByHost; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        ReservationGuest that = (ReservationGuest) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
