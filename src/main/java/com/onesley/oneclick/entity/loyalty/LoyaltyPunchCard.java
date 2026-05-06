package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.shared.PunchCardActivity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.loyalty_punch_cards} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "loyalty_punch_cards")
public class LoyaltyPunchCard extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "activity_type", nullable = false, columnDefinition = "punch_card_activity")
    private PunchCardActivity activityType;

    @Column(name = "count_punched", nullable = false)
    private Integer countPunched;

    @Column(name = "threshold", nullable = false)
    private Integer threshold;

    @Column(name = "redeemed_count", nullable = false)
    private Integer redeemedCount;

    @Column(name = "last_punched_at")
    private Instant lastPunchedAt;

    @Column(name = "last_redeemed_at")
    private Instant lastRedeemedAt;

    protected LoyaltyPunchCard() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getClientId() { return clientId; }
    public PunchCardActivity getActivityType() { return activityType; }
    public Integer getCountPunched() { return countPunched; }
    public Integer getThreshold() { return threshold; }
    public Integer getRedeemedCount() { return redeemedCount; }
    public Instant getLastPunchedAt() { return lastPunchedAt; }
    public Instant getLastRedeemedAt() { return lastRedeemedAt; }
}
