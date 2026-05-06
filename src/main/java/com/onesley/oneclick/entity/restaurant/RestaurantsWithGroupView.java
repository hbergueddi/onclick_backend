package com.onesley.oneclick.entity.restaurant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Entité {@code public.v_restaurants_with_group} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : vue read-only @Immutable.
 */
@Entity
@Immutable
@Table(name = "v_restaurants_with_group")
public class RestaurantsWithGroupView {

    @Id
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @Column(name = "name", insertable = false, updatable = false)
    private String name;

    @Column(name = "city", insertable = false, updatable = false)
    private String city;

    @Column(name = "cuisine", insertable = false, updatable = false)
    private String cuisine;

    @Column(name = "budget", insertable = false, updatable = false)
    private String budget;

    @Column(name = "rating", insertable = false, updatable = false)
    private BigDecimal rating;

    @Column(name = "image", insertable = false, updatable = false)
    private String image;

    @Column(name = "status", insertable = false, updatable = false)
    private String status;

    @Column(name = "group_id", insertable = false, updatable = false)
    private UUID groupId;

    @Column(name = "lounge_pts", insertable = false, updatable = false)
    private Integer loungePts;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "google_rating", insertable = false, updatable = false)
    private BigDecimal googleRating;

    @Column(name = "group_name", insertable = false, updatable = false)
    private String groupName;

    @Column(name = "group_owner_id", insertable = false, updatable = false)
    private UUID groupOwnerId;

    protected RestaurantsWithGroupView() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getCuisine() { return cuisine; }
    public String getBudget() { return budget; }
    public BigDecimal getRating() { return rating; }
    public String getImage() { return image; }
    public String getStatus() { return status; }
    public UUID getGroupId() { return groupId; }
    public Integer getLoungePts() { return loungePts; }
    public Instant getCreatedAt() { return createdAt; }
    public BigDecimal getGoogleRating() { return googleRating; }
    public String getGroupName() { return groupName; }
    public UUID getGroupOwnerId() { return groupOwnerId; }
}
