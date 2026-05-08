package com.onesley.oneclick.entity.restaurant;

import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.shared.StaffRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.restaurant_staff} — junction Restaurant ↔ Profile avec
 * staff_role + audit (start_date, status, created_by, modified_by).
 *
 * <h3>Pattern : entité d'association (pas {@code @ManyToMany})</h3>
 * <p>UNIQUE composite {@code (user_id, restaurant_id)} confirmé en DB
 * ({@code restaurant_staff_user_id_restaurant_id_key}). Mais la PK est
 * {@code id} séparée (pas composite) → 2× {@code @ManyToOne} simples,
 * <b>pas de {@code @MapsId}</b>.
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code user_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code restaurant_id NOT NULL} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code created_by} / {@code modified_by} → audits, restent UUID brut.</li>
 * </ul>
 */
@Entity
@Table(
    name = "restaurant_staff",
    uniqueConstraints = @UniqueConstraint(
        name = "restaurant_staff_user_id_restaurant_id_key",
        columnNames = {"user_id", "restaurant_id"}
    )
)
@EntityListeners(AuditingEntityListener.class)
public class RestaurantStaff {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // ─── Jointure user_id ───────────────────────────────────────────────────
    @Column(name = "user_id", nullable = false, insertable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Profile user;

    // ─── Jointure restaurant_id ─────────────────────────────────────────────
    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @NotNull
    @Column(name = "staff_role", nullable = false, columnDefinition = "staff_role")
    private StaffRole staffRole;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "start_date")
    private LocalDate startDate;

    /** Audit field : UUID brut. */
    @Column(name = "created_by")
    private UUID createdBy;

    /** Audit field : UUID brut. */
    @Column(name = "modified_by")
    private UUID modifiedBy;

    protected RestaurantStaff() {
        // JPA
    }

    public UUID getId() { return id; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getUserId() { return userId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Profile getUser() { return user; }
    public void setUser(Profile user) { this.user = user; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getRestaurantId() { return restaurantId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }

    public StaffRole getStaffRole() { return staffRole; }
    public Instant getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getModifiedBy() { return modifiedBy; }

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
        RestaurantStaff that = (RestaurantStaff) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
