package com.onesley.oneclick.entity.restaurant;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entité {@code public.restaurants} — table métier centrale (1042 lignes en prod).
 *
 * <p>Pattern pilote : <i>worst-case du schéma</i> — couvre simultanément :
 * <ul>
 *   <li>JSONB structuré ({@code opening_hours} → {@code List<OpeningHourSlot>})</li>
 *   <li>2× ARRAY text ({@code tags}, {@code google_photos})</li>
 *   <li>numeric précis ({@code rating}, {@code latitude}, {@code longitude})</li>
 *   <li>héritage {@link TimestampedEntity}</li>
 *   <li>FKs UUID brutes ({@code group_id}, {@code tenant_id}, {@code referred_by_id})</li>
 * </ul>
 *
 * <p><b>Colonne {@code search_vector tsvector} non mappée</b> : alimentée par un
 * trigger DB ({@code update_restaurant_search_vector}). Hibernate n'a pas de support
 * natif pour {@code tsvector}, et de toute façon l'écriture est exclusivement DB-side.
 * Pour la recherche fulltext on passera par une RPC ou {@code @Query(nativeQuery=true)}
 * en Phase 11.
 */
@Entity
@Table(name = "restaurants")
public class Restaurant extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "cuisine", nullable = false)
    private String cuisine;

    @Column(name = "budget", nullable = false)
    private String budget;

    @Column(name = "rating", nullable = false, precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(name = "reviews_count", nullable = false)
    private Integer reviewsCount;

    @Column(name = "image", nullable = false)
    private String image;

    @Column(name = "phone")
    private String phone;

    @Column(name = "address")
    private String address;

    @Column(name = "description")
    private String description;

    @Column(name = "open_now", nullable = false)
    private Boolean openNow;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    @Column(name = "lounge_pts", nullable = false)
    private Integer loungePts;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "max_staff", nullable = false)
    private Integer maxStaff;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "google_place_id")
    private String googlePlaceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opening_hours", columnDefinition = "jsonb")
    private List<OpeningHourSlot> openingHours;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "google_rating", precision = 3, scale = 1)
    private BigDecimal googleRating;

    @Column(name = "google_reviews_count")
    private Integer googleReviewsCount;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "google_photos", columnDefinition = "text[]")
    private List<String> googlePhotos = new ArrayList<>();

    @Column(name = "google_updated_at")
    private Instant googleUpdatedAt;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @Column(name = "referral_code")
    private String referralCode;

    @Column(name = "referred_by_id")
    private UUID referredById;

    @Column(name = "referred_activated_at")
    private Instant referredActivatedAt;

    @Column(name = "tenant_id")
    private UUID tenantId;

    protected Restaurant() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getCuisine() { return cuisine; }
    public String getBudget() { return budget; }
    public BigDecimal getRating() { return rating; }
    public Integer getReviewsCount() { return reviewsCount; }
    public String getImage() { return image; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getDescription() { return description; }
    public Boolean getOpenNow() { return openNow; }
    public List<String> getTags() { return tags; }
    public Integer getLoungePts() { return loungePts; }
    public String getStatus() { return status; }
    public Integer getMaxStaff() { return maxStaff; }
    public UUID getGroupId() { return groupId; }
    public String getGooglePlaceId() { return googlePlaceId; }
    public List<OpeningHourSlot> getOpeningHours() { return openingHours; }
    public String getWebsiteUrl() { return websiteUrl; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getGoogleRating() { return googleRating; }
    public Integer getGoogleReviewsCount() { return googleReviewsCount; }
    public List<String> getGooglePhotos() { return googlePhotos; }
    public Instant getGoogleUpdatedAt() { return googleUpdatedAt; }
    public Instant getOnboardingCompletedAt() { return onboardingCompletedAt; }
    public String getReferralCode() { return referralCode; }
    public UUID getReferredById() { return referredById; }
    public Instant getReferredActivatedAt() { return referredActivatedAt; }
    public UUID getTenantId() { return tenantId; }
}
