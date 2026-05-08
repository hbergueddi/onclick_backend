package com.onesley.oneclick.entity.support;

import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.reservation.Reservation;
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
 * Entité {@code public.no_show_disputes} — workflow contestation no-show 48h
 * (3 phases : resto 0-1h / support 1-48h / cron 48h+).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code reservation_id NOT NULL} → {@link Reservation} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code client_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code support_ticket_id} → {@link SupportTicket} en {@code @ManyToOne(LAZY)},
 *       nullable (escalation Phase 2 uniquement).</li>
 *   <li>{@code resolved_by} / {@code created_by} / {@code modified_by} : audits, UUID brut.</li>
 * </ul>
 */
@Entity
@Table(name = "no_show_disputes")
@EntityListeners(AuditingEntityListener.class)
public class NoShowDispute {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reservation_id", nullable = false, insertable = false, updatable = false)
    private UUID reservationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "client_id", nullable = false, insertable = false, updatable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Profile client;

    @NotBlank
    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "photo_url")
    private String photoUrl;

    @NotNull
    @Column(name = "is_recontestation", nullable = false)
    private Boolean isRecontestation;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

    @NotBlank
    @Column(name = "escalation_phase", nullable = false)
    private String escalationPhase;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Audit field : UUID brut. */
    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "support_ticket_id", insertable = false, updatable = false)
    private UUID supportTicketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "support_ticket_id")
    private SupportTicket supportTicket;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Audit field : UUID brut. */
    @Column(name = "created_by")
    private UUID createdBy;

    /** Audit field : UUID brut. */
    @Column(name = "modified_by")
    private UUID modifiedBy;

    protected NoShowDispute() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public Reservation getReservation() { return reservation; }
    public void setReservation(Reservation reservation) { this.reservation = reservation; }
    public UUID getClientId() { return clientId; }
    public Profile getClient() { return client; }
    public void setClient(Profile client) { this.client = client; }
    public String getDescription() { return description; }
    public String getPhotoUrl() { return photoUrl; }
    public Boolean getIsRecontestation() { return isRecontestation; }
    public String getStatus() { return status; }
    public String getEscalationPhase() { return escalationPhase; }
    public String getResolutionNote() { return resolutionNote; }
    public Instant getResolvedAt() { return resolvedAt; }
    public UUID getResolvedBy() { return resolvedBy; }
    public UUID getSupportTicketId() { return supportTicketId; }
    public SupportTicket getSupportTicket() { return supportTicket; }
    public void setSupportTicket(SupportTicket supportTicket) { this.supportTicket = supportTicket; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getModifiedBy() { return modifiedBy; }

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
        NoShowDispute that = (NoShowDispute) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
