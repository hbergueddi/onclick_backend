package com.onesley.oneclick.entity.restaurant;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.status.EntityStatus;
import com.onesley.oneclick.entity.tenant.Tenant;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Entité {@code public.restaurants} — table métier centrale (1042 lignes en prod).
 *
 * <p>Pattern : <i>worst-case du schéma</i> — couvre simultanément :
 * <ul>
 *   <li>JSONB structuré ({@code opening_hours} → {@code List<OpeningHourSlot>})</li>
 *   <li>2× ARRAY text ({@code tags}, {@code google_photos})</li>
 *   <li>numeric précis ({@code rating}, {@code latitude}, {@code longitude})</li>
 *   <li>héritage {@link TimestampedEntity}</li>
 *   <li>Jointures matérialisées (passe 3) : {@link Tenant}, {@link RestaurantGroup},
 *       self-ref {@code referredBy}, {@link RestaurantTierStatu} 1-1 inverse,
 *       4 {@code @OneToMany} aggregate.</li>
 * </ul>
 *
 * <p><b>Colonne {@code search_vector tsvector} non mappée</b> : alimentée par un
 * trigger DB ({@code update_restaurant_search_vector}). Hibernate n'a pas de support
 * natif pour {@code tsvector}, et de toute façon l'écriture est exclusivement DB-side.
 * Pour la recherche fulltext on passera par une RPC ou {@code @Query(nativeQuery=true)}
 * en Phase 11.
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code tenant_id} → {@link Tenant} en {@code @ManyToOne(LAZY)}, nullable.</li>
 *   <li>{@code group_id} → {@link RestaurantGroup} en {@code @ManyToOne(LAZY)}, nullable.</li>
 *   <li>{@code referred_by_id} → self-ref {@code Restaurant} (LAZY obligatoire pour
 *       éviter une boucle de chargement).</li>
 *   <li>{@code google_place_id} reste {@link String} — Google Places ID, pas une FK UUID.</li>
 *   <li>{@code @OneToOne(mappedBy="restaurant") tierStatus} : 1-1 strict, owner =
 *       {@link RestaurantTierStatu} (UNIQUE INDEX en DB sur {@code restaurant_id} confirmé).</li>
 * </ul>
 *
 * <h3>Aggregate boundaries (DDD)</h3>
 * <ul>
 *   <li>{@code services} (3-6/resto) : cascade {PERSIST, MERGE}, pas orphanRemoval —
 *       trigger DB crée services par défaut, puis admin peut les modifier indépendamment.</li>
 *   <li>{@code zones} (1-5/resto) : cascade {PERSIST, MERGE}. Delete zone refuse si
 *       elle a des tables (NOT NULL en DB) — réassignation explicite via service.</li>
 *   <li>{@code media} (~10) : cascade ALL + orphanRemoval — photos appartiennent
 *       strictement au resto.</li>
 *   <li>{@code gainRules} (~1-5) : cascade ALL + orphanRemoval — règles de fidélité
 *       config strictement liée au resto.</li>
 *   <li><b>Pas d'inverse</b> vers {@code reservations}, {@code loyaltyPoints},
 *       {@code scannedTickets}, {@code staff}, {@code offers} — volume non borné,
 *       repositories paginés à la place.</li>
 * </ul>
 *
 * <p>{@code @DynamicUpdate} : évite l'UPDATE des 30+ colonnes à chaque save quand
 * seul 1-2 champs changent (ex: {@code open_now}, {@code rating}).
 */
@Entity
@Table(name = "restaurants")
@DynamicUpdate
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

    @Column(name = "status_id", nullable = false, insertable = false, updatable = false)
    private UUID statusId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private EntityStatus status;

    @Column(name = "max_staff", nullable = false)
    private Integer maxStaff;

    // ─── Jointure group_id (RestaurantGroup, nullable) ──────────────────────
    @Column(name = "group_id", insertable = false, updatable = false)
    private UUID groupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private RestaurantGroup group;

    /** {@code google_place_id text} — String Google Places, pas une FK UUID. */
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

    // ─── Jointure self-ref referred_by_id (parrainage inter-restos, nullable) ─
    @Column(name = "referred_by_id", insertable = false, updatable = false)
    private UUID referredById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_by_id")
    private Restaurant referredBy;

    @Column(name = "referred_activated_at")
    private Instant referredActivatedAt;

    // ─── Jointure tenant_id (Tenant whitelabel, nullable) ───────────────────
    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    // ─── Inverse @OneToOne tierStatus (1-1 confirmé en DB UNIQUE) ───────────
    @OneToOne(mappedBy = "restaurant", fetch = FetchType.LAZY)
    private RestaurantTierStatu tierStatus;

    // ─── Aggregate members ──────────────────────────────────────────────────
    @OneToMany(mappedBy = "restaurant", fetch = FetchType.LAZY,
               cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @BatchSize(size = 50)
    private Set<RestaurantService> services = new HashSet<>();

    @OneToMany(mappedBy = "restaurant", fetch = FetchType.LAZY,
               cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @BatchSize(size = 50)
    private Set<RestaurantZone> zones = new HashSet<>();

    @OneToMany(mappedBy = "restaurant", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private Set<RestaurantMedia> media = new HashSet<>();

    @OneToMany(mappedBy = "restaurant", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private Set<RestaurantGainRule> gainRules = new HashSet<>();

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
    public UUID getStatusId() { return statusId; }
    public EntityStatus getStatus() { return status; }
    public void setStatus(EntityStatus status) { this.status = status; }
    public Integer getMaxStaff() { return maxStaff; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getGroupId() { return groupId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public RestaurantGroup getGroup() { return group; }
    public void setGroup(RestaurantGroup group) { this.group = group; }

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

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getReferredById() { return referredById; }
    /** Lazy load self-ref — peut être null. */
    public Restaurant getReferredBy() { return referredBy; }
    public void setReferredBy(Restaurant referredBy) { this.referredBy = referredBy; }

    public Instant getReferredActivatedAt() { return referredActivatedAt; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getTenantId() { return tenantId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    /** Lazy 1-1 inverse — peut être null si le resto n'a pas de tier status. */
    public RestaurantTierStatu getTierStatus() { return tierStatus; }

    public Set<RestaurantService> getServices() { return services; }
    public Set<RestaurantZone> getZones() { return zones; }
    public Set<RestaurantMedia> getMedia() { return media; }
    public Set<RestaurantGainRule> getGainRules() { return gainRules; }

    // ─── Helpers bidirectionnels (cohérence mémoire des deux côtés) ─────────

    public void addService(RestaurantService service) {
        services.add(service);
        service.setRestaurant(this);
    }
    public void removeService(RestaurantService service) {
        services.remove(service);
        service.setRestaurant(null);
    }

    public void addZone(RestaurantZone zone) {
        zones.add(zone);
        zone.setRestaurant(this);
    }
    public void removeZone(RestaurantZone zone) {
        zones.remove(zone);
        zone.setRestaurant(null);
    }

    public void addMedia(RestaurantMedia m) {
        media.add(m);
        m.setRestaurant(this);
    }
    public void removeMedia(RestaurantMedia m) {
        media.remove(m);
        m.setRestaurant(null);  // déclenche orphanRemoval au flush
    }

    public void addGainRule(RestaurantGainRule rule) {
        gainRules.add(rule);
        rule.setRestaurant(this);
    }
    public void removeGainRule(RestaurantGainRule rule) {
        gainRules.remove(rule);
        rule.setRestaurant(null);  // déclenche orphanRemoval au flush
    }

    // ─── equals / hashCode anti-proxy LAZY ──────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Restaurant that = (Restaurant) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
