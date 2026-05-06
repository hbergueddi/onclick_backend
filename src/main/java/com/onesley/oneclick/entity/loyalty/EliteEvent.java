package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entité {@code public.elite_events} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "elite_events")
public class EliteEvent extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

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
}
