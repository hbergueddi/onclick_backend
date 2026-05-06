package com.onesley.oneclick.entity.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.client_ratings} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : entité simple.
 */
@Entity
@Table(name = "client_ratings")
public class ClientRating {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "rating", nullable = false, precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "total_honored", nullable = false)
    private Integer totalHonored;

    @Column(name = "total_no_show", nullable = false)
    private Integer totalNoShow;

    @Column(name = "is_new", nullable = false)
    private Boolean isNew;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "visible_rating", nullable = false, precision = 2, scale = 1)
    private BigDecimal visibleRating;

    @Column(name = "visible_total_honored", nullable = false)
    private Integer visibleTotalHonored;

    @Column(name = "visible_total_no_show", nullable = false)
    private Integer visibleTotalNoShow;

    @Column(name = "visible_is_new", nullable = false)
    private Boolean visibleIsNew;

    @Column(name = "pending_rating", precision = 2, scale = 1)
    private BigDecimal pendingRating;

    @Column(name = "pending_total_honored")
    private Integer pendingTotalHonored;

    @Column(name = "pending_total_no_show")
    private Integer pendingTotalNoShow;

    @Column(name = "pending_is_new")
    private Boolean pendingIsNew;

    @Column(name = "pending_visible_at")
    private Instant pendingVisibleAt;

    protected ClientRating() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public BigDecimal getRating() { return rating; }
    public Integer getTotalHonored() { return totalHonored; }
    public Integer getTotalNoShow() { return totalNoShow; }
    public Boolean getIsNew() { return isNew; }
    public Instant getUpdatedAt() { return updatedAt; }
    public BigDecimal getVisibleRating() { return visibleRating; }
    public Integer getVisibleTotalHonored() { return visibleTotalHonored; }
    public Integer getVisibleTotalNoShow() { return visibleTotalNoShow; }
    public Boolean getVisibleIsNew() { return visibleIsNew; }
    public BigDecimal getPendingRating() { return pendingRating; }
    public Integer getPendingTotalHonored() { return pendingTotalHonored; }
    public Integer getPendingTotalNoShow() { return pendingTotalNoShow; }
    public Boolean getPendingIsNew() { return pendingIsNew; }
    public Instant getPendingVisibleAt() { return pendingVisibleAt; }
}
