package com.onesley.oneclick.entity.loyalty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Entité {@code public.v_client_loyalty_summary} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : vue read-only @Immutable.
 */
@Entity
@Immutable
@Table(name = "v_client_loyalty_summary")
public class ClientLoyaltySummaryView {

    @Id
    @Column(name = "client_id", insertable = false, updatable = false)
    private UUID clientId;

    @Column(name = "first_name", insertable = false, updatable = false)
    private String firstName;

    @Column(name = "last_name", insertable = false, updatable = false)
    private String lastName;

    @Column(name = "city", insertable = false, updatable = false)
    private String city;

    @Column(name = "phone", insertable = false, updatable = false)
    private String phone;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "total_points_earned", insertable = false, updatable = false)
    private Long totalPointsEarned;

    @Column(name = "available_points", insertable = false, updatable = false)
    private Long availablePoints;

    @Column(name = "total_spent", insertable = false, updatable = false)
    private BigDecimal totalSpent;

    @Column(name = "restaurants_visited", insertable = false, updatable = false)
    private Long restaurantsVisited;

    @Column(name = "total_reservations", insertable = false, updatable = false)
    private Long totalReservations;

    @Column(name = "reservations_honorees", insertable = false, updatable = false)
    private Long reservationsHonorees;

    @Column(name = "reservations_no_show", insertable = false, updatable = false)
    private Long reservationsNoShow;

    @Column(name = "tickets_scanned", insertable = false, updatable = false)
    private Long ticketsScanned;

    protected ClientLoyaltySummaryView() {
        // JPA
    }

    public UUID getClientId() { return clientId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getCity() { return city; }
    public String getPhone() { return phone; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getTotalPointsEarned() { return totalPointsEarned; }
    public Long getAvailablePoints() { return availablePoints; }
    public BigDecimal getTotalSpent() { return totalSpent; }
    public Long getRestaurantsVisited() { return restaurantsVisited; }
    public Long getTotalReservations() { return totalReservations; }
    public Long getReservationsHonorees() { return reservationsHonorees; }
    public Long getReservationsNoShow() { return reservationsNoShow; }
    public Long getTicketsScanned() { return ticketsScanned; }
}
