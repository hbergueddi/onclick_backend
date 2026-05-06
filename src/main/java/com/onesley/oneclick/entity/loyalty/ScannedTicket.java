package com.onesley.oneclick.entity.loyalty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.scanned_tickets} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "scanned_tickets")
@EntityListeners(AuditingEntityListener.class)
public class ScannedTicket {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "scanned_by", nullable = false)
    private UUID scannedBy;

    @Column(name = "ticket_ref", nullable = false)
    private String ticketRef;

    @Column(name = "montant", nullable = false)
    private BigDecimal montant;

    @Column(name = "points_credites", nullable = false)
    private Integer pointsCredites;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items", columnDefinition = "jsonb")
    private Map<String, Object> items = new HashMap<>();

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "photo_url")
    private String photoUrl;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "modified_by")
    private UUID modifiedBy;

    protected ScannedTicket() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getClientId() { return clientId; }
    public UUID getScannedBy() { return scannedBy; }
    public String getTicketRef() { return ticketRef; }
    public BigDecimal getMontant() { return montant; }
    public Integer getPointsCredites() { return pointsCredites; }
    public Map<String, Object> getItems() { return items; }
    public String getStatus() { return status; }
    public String getPhotoUrl() { return photoUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getReservationId() { return reservationId; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getModifiedBy() { return modifiedBy; }
}
