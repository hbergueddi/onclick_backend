package com.onesley.oneclick.entity.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Entité {@code public.client_visible_ratings} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : vue read-only @Immutable.
 */
@Entity
@Immutable
@Table(name = "client_visible_ratings")
public class ClientVisibleRating {

    @Id
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", insertable = false, updatable = false)
    private UUID clientId;

    @Column(name = "rating", insertable = false, updatable = false)
    private BigDecimal rating;

    @Column(name = "total_honored", insertable = false, updatable = false)
    private Integer totalHonored;

    @Column(name = "total_no_show", insertable = false, updatable = false)
    private Integer totalNoShow;

    @Column(name = "is_new", insertable = false, updatable = false)
    private Boolean isNew;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "has_pending", insertable = false, updatable = false)
    private Boolean hasPending;

    @Column(name = "pending_visible_at", insertable = false, updatable = false)
    private Instant pendingVisibleAt;

    protected ClientVisibleRating() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public BigDecimal getRating() { return rating; }
    public Integer getTotalHonored() { return totalHonored; }
    public Integer getTotalNoShow() { return totalNoShow; }
    public Boolean getIsNew() { return isNew; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Boolean getHasPending() { return hasPending; }
    public Instant getPendingVisibleAt() { return pendingVisibleAt; }
}
