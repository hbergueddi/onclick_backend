package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.restaurant.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.elite_events} — événements VIP du Elite Club (Ruby/
 * Sapphire/Émeraude/Black) restreints par tier minimum.
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code restaurant_id} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, nullable
 *       (un événement Elite peut être hors-resto : croisière, soirée privée…).</li>
 *   <li>Côté inverse {@code @OneToMany rsvps} : non ajouté — volume potentiellement
 *       50-200, repository paginé à la place pour la liste des participants.</li>
 * </ul>
 */
@Entity
@Table(name = "elite_events")
public class EliteEvent extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @NotBlank
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @NotNull
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "event_time")
    private String eventTime;

    @Column(name = "location")
    private String location;

    @Column(name = "max_places")
    private Integer maxPlaces;

    @Column(name = "remaining_places")
    private Integer remainingPlaces;

    @Column(name = "min_tier")
    private String minTier;

    @Column(name = "image")
    private String image;

    @Column(name = "is_active")
    private Boolean isActive;

    protected EliteEvent() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getEventDate() { return eventDate; }
    public String getEventTime() { return eventTime; }
    public String getLocation() { return location; }
    public Integer getMaxPlaces() { return maxPlaces; }
    public Integer getRemainingPlaces() { return remainingPlaces; }
    public String getMinTier() { return minTier; }
    public String getImage() { return image; }
    public Boolean getIsActive() { return isActive; }

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
        EliteEvent that = (EliteEvent) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
