package com.onesley.oneclick.entity.admin;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entité {@code public.fraud_alerts} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "fraud_alerts")
public class FraudAlert extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "montant")
    private BigDecimal montant;

    protected FraudAlert() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getType() { return type; }
    public String getSeverity() { return severity; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getClientId() { return clientId; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public BigDecimal getMontant() { return montant; }
}
