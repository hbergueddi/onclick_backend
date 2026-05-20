package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Système de notation client 0-5 — Sprint H.
 *
 * <p>3 colonnes parallèles (rating / visible_rating / pending_rating) pour gérer
 * la visibilité différée (cf feedback_delayed_visibility_rating.md).
 */
@Entity
@Table(name = "client_ratings")
@Getter
public class ClientRating extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id")
    @Setter private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    @Setter private UUID userId;

    @Column(name = "reservation_id")
    @Setter private UUID reservationId;

    @Column(nullable = false, precision = 2, scale = 1)
    @Setter private BigDecimal rating = new BigDecimal("5.0");

    @Column(name = "visible_rating", nullable = false, precision = 2, scale = 1)
    @Setter private BigDecimal visibleRating = new BigDecimal("5.0");

    @Column(name = "pending_rating", precision = 2, scale = 1)
    @Setter private BigDecimal pendingRating;

    @Column(length = 1024) @Setter private String reason;

    @Column(precision = 2, scale = 1)
    @Setter private BigDecimal delta;

    @Column(name = "deleted_at")
    @Setter private java.time.Instant deletedAt;
}
