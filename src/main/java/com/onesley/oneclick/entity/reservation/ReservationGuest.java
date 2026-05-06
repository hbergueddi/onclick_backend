package com.onesley.oneclick.entity.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.reservation_guests} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "reservation_guests")
@EntityListeners(AuditingEntityListener.class)
public class ReservationGuest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @NotNull
    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "guest_name")
    private String guestName;

    @NotBlank
    @Column(name = "guest_phone", nullable = false)
    private String guestPhone;

    @Column(name = "guest_user_id")
    private UUID guestUserId;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

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
    public UUID getReservationId() { return reservationId; }
    public UUID getInvitedBy() { return invitedBy; }
    public String getGuestName() { return guestName; }
    public String getGuestPhone() { return guestPhone; }
    public UUID getGuestUserId() { return guestUserId; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Boolean getSeenByHost() { return seenByHost; }
}
