package com.onesley.oneclick.entity.restaurant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.v_restaurants_google} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : vue read-only @Immutable.
 */
@Entity
@Immutable
@Table(name = "v_restaurants_google")
public class RestaurantsGoogleView {

    @Id
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @Column(name = "name", insertable = false, updatable = false)
    private String name;

    @Column(name = "google_place_id", insertable = false, updatable = false)
    private String googlePlaceId;

    @Column(name = "google_rating", insertable = false, updatable = false)
    private BigDecimal googleRating;

    @Column(name = "google_reviews_count", insertable = false, updatable = false)
    private Integer googleReviewsCount;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "google_photos", insertable = false, updatable = false)
    private List<String> googlePhotos = new ArrayList<>();

    @Column(name = "website_url", insertable = false, updatable = false)
    private String websiteUrl;

    @Column(name = "latitude", insertable = false, updatable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", insertable = false, updatable = false)
    private BigDecimal longitude;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opening_hours", insertable = false, updatable = false)
    private Map<String, Object> openingHours = new HashMap<>();

    @Column(name = "google_updated_at", insertable = false, updatable = false)
    private Instant googleUpdatedAt;

    protected RestaurantsGoogleView() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getGooglePlaceId() { return googlePlaceId; }
    public BigDecimal getGoogleRating() { return googleRating; }
    public Integer getGoogleReviewsCount() { return googleReviewsCount; }
    public List<String> getGooglePhotos() { return googlePhotos; }
    public String getWebsiteUrl() { return websiteUrl; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public Map<String, Object> getOpeningHours() { return openingHours; }
    public Instant getGoogleUpdatedAt() { return googleUpdatedAt; }
}
