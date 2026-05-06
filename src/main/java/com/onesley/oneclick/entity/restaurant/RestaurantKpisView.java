package com.onesley.oneclick.entity.restaurant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Entité {@code public.v_restaurant_kpis} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : vue read-only @Immutable.
 */
@Entity
@Immutable
@Table(name = "v_restaurant_kpis")
public class RestaurantKpisView {

    @Id
    @Column(name = "restaurant_id", insertable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "name", insertable = false, updatable = false)
    private String name;

    @Column(name = "city", insertable = false, updatable = false)
    private String city;

    @Column(name = "group_id", insertable = false, updatable = false)
    private UUID groupId;

    @Column(name = "total_ca", insertable = false, updatable = false)
    private BigDecimal totalCa;

    @Column(name = "ticket_count", insertable = false, updatable = false)
    private Long ticketCount;

    @Column(name = "total_points_issued", insertable = false, updatable = false)
    private Long totalPointsIssued;

    @Column(name = "total_reservations", insertable = false, updatable = false)
    private Long totalReservations;

    @Column(name = "reservations_honorees", insertable = false, updatable = false)
    private Long reservationsHonorees;

    @Column(name = "reservations_no_show", insertable = false, updatable = false)
    private Long reservationsNoShow;

    @Column(name = "active_reservations", insertable = false, updatable = false)
    private Long activeReservations;

    @Column(name = "staff_count", insertable = false, updatable = false)
    private Long staffCount;

    protected RestaurantKpisView() {
        // JPA
    }

    public UUID getRestaurantId() { return restaurantId; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public UUID getGroupId() { return groupId; }
    public BigDecimal getTotalCa() { return totalCa; }
    public Long getTicketCount() { return ticketCount; }
    public Long getTotalPointsIssued() { return totalPointsIssued; }
    public Long getTotalReservations() { return totalReservations; }
    public Long getReservationsHonorees() { return reservationsHonorees; }
    public Long getReservationsNoShow() { return reservationsNoShow; }
    public Long getActiveReservations() { return activeReservations; }
    public Long getStaffCount() { return staffCount; }
}
