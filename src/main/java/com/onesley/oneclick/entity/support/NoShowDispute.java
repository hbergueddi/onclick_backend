package com.onesley.oneclick.entity.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.no_show_disputes} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "no_show_disputes")
@EntityListeners(AuditingEntityListener.class)
public class NoShowDispute {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "is_recontestation", nullable = false)
    private Boolean isRecontestation;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "escalation_phase", nullable = false)
    private String escalationPhase;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "support_ticket_id")
    private UUID supportTicketId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "modified_by")
    private UUID modifiedBy;

    protected NoShowDispute() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public UUID getClientId() { return clientId; }
    public String getDescription() { return description; }
    public String getPhotoUrl() { return photoUrl; }
    public Boolean getIsRecontestation() { return isRecontestation; }
    public String getStatus() { return status; }
    public String getEscalationPhase() { return escalationPhase; }
    public String getResolutionNote() { return resolutionNote; }
    public Instant getResolvedAt() { return resolvedAt; }
    public UUID getResolvedBy() { return resolvedBy; }
    public UUID getSupportTicketId() { return supportTicketId; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getModifiedBy() { return modifiedBy; }
}
