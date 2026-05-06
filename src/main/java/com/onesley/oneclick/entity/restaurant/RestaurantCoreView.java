package com.onesley.oneclick.entity.restaurant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité mappée sur la vue {@code public.v_restaurants_core}.
 *
 * <p>Pattern pilote : <i>vue read-only via {@code @Immutable}</i>.
 *
 * <p>Cette vue est une projection minimale (12 colonnes) du restaurant pour les
 * listes côté client (page Explore, cards). Elle accélère la sérialisation en
 * évitant les colonnes lourdes (JSONB opening_hours, ARRAY google_photos, etc.).
 *
 * <p>Pourquoi {@code @Immutable} :
 * <ul>
 *   <li>{@code @Immutable} dit à Hibernate de ne JAMAIS générer de SQL UPDATE
 *       pour cette entité, même si on a un repository qui hérite save() (le
 *       save() devient un no-op après le persist initial qui ne peut pas
 *       arriver puisque la vue n'est pas insertable côté Postgres).</li>
 *   <li>Hibernate ignore aussi le check d'optimistic locking et certains
 *       flushs sur cette entité — gain de performance pour les lectures pures.</li>
 * </ul>
 *
 * <p>Les 7 vues de la base ({@code v_admin_audit_log}, {@code v_client_loyalty_summary},
 * {@code v_restaurant_kpis}, etc.) suivront ce même pattern dans le scaffolder.
 */
@Entity
@Immutable
@Table(name = "v_restaurants_core")
public class RestaurantCoreView {

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

    @Column(name = "rating", insertable = false, updatable = false, precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "reviews_count", insertable = false, updatable = false)
    private Integer reviewsCount;

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

    protected RestaurantCoreView() {
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
    public String getStatus() { return status; }
    public UUID getGroupId() { return groupId; }
    public Integer getLoungePts() { return loungePts; }
    public Instant getCreatedAt() { return createdAt; }
}
