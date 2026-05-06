package com.onesley.oneclick.entity.marketing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.offer_impressions} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : entité simple.
 */
@Entity
@Table(name = "offer_impressions")
public class OfferImpression {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    @Column(name = "source", nullable = false)
    private String source;

    protected OfferImpression() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getOfferId() { return offerId; }
    public UUID getUserId() { return userId; }
    public Instant getViewedAt() { return viewedAt; }
    public String getSource() { return source; }
}
