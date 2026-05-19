package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.modules.restaurant.api.RestaurantDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Restaurant partenaire — fiche catalogue (§4).
 */
@Entity
@Table(name = "restaurants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Restaurant extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotBlank
    @Column(name = "name", nullable = false)
    @Setter @Size(max = 255) private String name;

    @Column(name = "description")
    @Setter @Size(max = 2000) private String description;

    @Column(name = "phone")
    @Setter @Size(max = 255) private String phone;

    @Column(name = "address")
    @Setter @Size(max = 2000) private String address;

    @NotBlank
    @Column(name = "city", nullable = false)
    @Setter @Size(max = 255) private String city;

    @Column(name = "latitude", precision = 10, scale = 7)
    @Setter private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    @Setter private BigDecimal longitude;

    @Pattern(regexp = "^(active|paused|archived)$")
    @Column(name = "status", nullable = false)
    @Setter @Size(max = 255) @NotBlank private String status = "active";

    // ─── V16 — attributs éditoriaux Pocket ──────────────────────────────────
    // Nullables pour rétro-compat avec les rows pré-V16.

    @Pattern(regexp = "^(€|€€|€€€)$")
    @Column(name = "budget")
    @Setter @Size(max = 512) private String budget;

    /**
     * Étiquettes thématiques libres (cuisine, ambiance, etc.).
     * Mappé directement sur la colonne PostgreSQL {@code text[]}.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    @Setter private String[] tags;

    @Min(0)
    @Column(name = "lounge_pts", nullable = false)
    @Setter @PositiveOrZero private Integer loungePts = 0;

    @Column(name = "image")
    @Setter @Size(max = 1024) private String image;

    // ─── V24 — Sprint K : champs exploités par l'admin (RestaurantFormDialog) ──

    /** Type de cuisine éditorial (Marocain, Italien, …). */
    @Column(name = "cuisine")
    @Setter @Size(max = 512) private String cuisine;

    /** Plafond de staff actifs — workflow demande d'augmentation (Journal). */
    @Min(0)
    @Column(name = "max_staff")
    @Setter @PositiveOrZero private Integer maxStaff;

    /** Groupe propriétaire (chaîne multi-restaurants) — nullable si indépendant. */
    @Column(name = "group_id")
    @Setter private UUID groupId;

    // ─── Google Places enrichment (cols V23 + service GooglePlacesEnrichmentService) ──
    // Avant ce mapping JPA : colonnes peuplées en native SQL mais invisibles dans
    // les responses /api/restaurants → frontend OneClickCompass affichait des
    // ratings/hours toujours null malgré DB enrichie à 99.9%.

    /** Google Place ID (identifiant unique Google). */
    @Column(name = "google_place_id", length = 255)
    @Size(max = 255) private String googlePlaceId;

    /** Note Google (0.0-5.0). */
    @Column(name = "google_rating", precision = 2, scale = 1)
    private BigDecimal googleRating;

    /** Nombre de reviews Google. */
    @Column(name = "google_reviews_count")
    private Integer googleReviewsCount;

    /** Site web officiel récupéré via Google Places. */
    @Column(name = "website_url")
    @Size(max = 1024) private String websiteUrl;

    /**
     * Horaires d'ouverture (JSONB structure regularOpeningHours Google).
     * Mappé en String brut — le frontend désérialise.
     */
    @Column(name = "opening_hours", columnDefinition = "jsonb")
    @Size(max = 512) private String openingHours;

    /** Timestamp dernier appel Google Places (utilisé pour skip enrichments idempotents). */
    @Column(name = "google_updated_at")
    private java.time.Instant googleUpdatedAt;

    public Restaurant(UUID id, Tenant tenant, String name, String city) {
        this.id = id;
        this.tenant = tenant;
        this.name = name;
        this.city = city;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public RestaurantDto toDto() {
        return new RestaurantDto(id, tenantId, name, description, phone, address, city,
            latitude, longitude, status, budget,
            tags == null ? java.util.List.of() : java.util.List.of(tags),
            loungePts == null ? 0 : loungePts, image, cuisine, maxStaff, groupId,
            googlePlaceId, googleRating, googleReviewsCount, websiteUrl, openingHours, googleUpdatedAt,
            getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        Restaurant that = (Restaurant) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
