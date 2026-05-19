package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.reservation.api.ReservationGuestDto;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Invité d'une réservation — un des 3 identifiants au minimum (CHECK constraint V17) :
 *  - {@code guestUser} : user OneClick existant (FK)
 *  - {@code guestPhone} : téléphone d'un user pas encore inscrit (futur signup)
 *  - {@code guestName} : nom libre (placeholder informatif)
 *
 * <p>Workflow {@code status} (V17) :
 * <pre>
 *  linked   → auto-attaché à une résa (default, ex: organisateur ajoute un user OneClick)
 *  invited  → invitation envoyée par notif/SMS, en attente de réponse
 *  accepted → guest a accepté de participer
 *  refused  → guest a décliné (notifier organisateur)
 *  cancelled → guest s'est désisté APRÈS avoir accepté (notifier organisateur)
 * </pre>
 *
 * <p>{@code seenByHost} : marque que l'organisateur a vu la réponse (badge UI). MAJ
 * automatique côté service quand l'organisateur consulte la liste.
 */
@Entity
@Table(name = "reservation_guests")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
    @Setter private String guestName;

    @Column(name = "guest_phone")
    private String guestPhone;

    @Column(name = "invited_by", insertable = false, updatable = false)
    private UUID invitedById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by")
    private User invitedBy;

    @Column(name = "status", nullable = false)
    @Setter private String status = "linked";

    @Column(name = "seen_by_host", nullable = false)
    @Setter private boolean seenByHost = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public ReservationGuest(UUID id, Reservation reservation, User guestUser, String guestName,
                            String guestPhone, User invitedBy, String status) {
        this.id = id;
        this.reservation = reservation;
        this.guestUser = guestUser;
        this.guestName = guestName;
        this.guestPhone = guestPhone;
        this.invitedBy = invitedBy;
        if (status != null) this.status = status;
    }

    // ─── Getters ───────────────────────────────────────────────────────

    // ─── Setters (workflow) ────────────────────────────────────────────

    // ─── toDto (pattern senior — internal → api autorisé en Modulith) ──

    public ReservationGuestDto toDto() {
        return new ReservationGuestDto(
            id,
            reservationId,
            guestUserId,
            guestName,
            guestPhone,
            invitedById,
            status,
            seenByHost,
            createdAt
        );
    }

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
