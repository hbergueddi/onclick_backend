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

/**
 * Restaurant partenaire — fiche catalogue (§4).
 */
@Entity
@Table(name = "restaurants")
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
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "phone")
    private String phone;

    @Column(name = "address")
    private String address;

    @NotBlank
    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Pattern(regexp = "^(active|paused|archived)$")
    @Column(name = "status", nullable = false)
    private String status = "active";

    // ─── V16 — attributs éditoriaux Pocket ──────────────────────────────────
    // Nullables pour rétro-compat avec les rows pré-V16.

    @Pattern(regexp = "^(€|€€|€€€)$")
    @Column(name = "budget")
    private String budget;

    /**
     * Étiquettes thématiques libres (cuisine, ambiance, etc.).
     * Mappé directement sur la colonne PostgreSQL {@code text[]}.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;

    @Min(0)
    @Column(name = "lounge_pts", nullable = false)
    private Integer loungePts = 0;

    @Column(name = "image")
    private String image;

    // ─── V24 — Sprint K : champs exploités par l'admin (RestaurantFormDialog) ──

    /** Type de cuisine éditorial (Marocain, Italien, …). */
    @Column(name = "cuisine")
    private String cuisine;

    /** Plafond de staff actifs — workflow demande d'augmentation (Journal). */
    @Min(0)
    @Column(name = "max_staff")
    private Integer maxStaff;

    /** Groupe propriétaire (chaîne multi-restaurants) — nullable si indépendant. */
    @Column(name = "group_id")
    private UUID groupId;

    protected Restaurant() {
        // JPA
    }

    public Restaurant(UUID id, Tenant tenant, String name, String city) {
        this.id = id;
        this.tenant = tenant;
        this.name = name;
        this.city = city;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Tenant getTenant() { return tenant; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBudget() { return budget; }
    public void setBudget(String budget) { this.budget = budget; }
    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }
    public Integer getLoungePts() { return loungePts; }
    public void setLoungePts(Integer loungePts) { this.loungePts = loungePts; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public String getCuisine() { return cuisine; }
    public void setCuisine(String cuisine) { this.cuisine = cuisine; }
    public Integer getMaxStaff() { return maxStaff; }
    public void setMaxStaff(Integer maxStaff) { this.maxStaff = maxStaff; }
    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }

    /** Mapping vers le DTO public exposé hors du module. */
    public RestaurantDto toDto() {
        return new RestaurantDto(id, tenantId, name, description, phone, address, city,
            latitude, longitude, status, budget,
            tags == null ? java.util.List.of() : java.util.List.of(tags),
            loungePts == null ? 0 : loungePts, image, cuisine, maxStaff, groupId, getCreatedAt());
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
