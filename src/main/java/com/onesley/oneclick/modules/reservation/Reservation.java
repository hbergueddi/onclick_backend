package com.onesley.oneclick.modules.reservation;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.core.identity.User;
import com.onesley.oneclick.core.tenant.Tenant;
import com.onesley.oneclick.modules.restaurant.Restaurant;
import com.onesley.oneclick.modules.restaurant.RestaurantService;
import com.onesley.oneclick.modules.restaurant.RestaurantTable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Réservation — workflow 7 statuts. {@code reservation_at} timestamptz UNIFIÉ.
 *
 * <p>Refonte vs legacy : avant on avait {@code date} (LocalDate) + {@code heure} (String "HH:MM").
 * Maintenant {@code reservation_at} (Instant timestamptz UTC) — query/sort/index plus simples,
 * gestion timezone explicite côté serveur.
 */
@Entity
@Table(name = "reservations")
public class Reservation extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "client_id", nullable = false, insertable = false, updatable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "table_id", insertable = false, updatable = false)
    private UUID tableId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private RestaurantTable table;

    @Column(name = "service_id", insertable = false, updatable = false)
    private UUID serviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private RestaurantService service;

    @NotNull
    @Column(name = "reservation_at", nullable = false)
    private Instant reservationAt;

    @Min(1)
    @Column(name = "guest_count", nullable = false)
    private Integer guestCount;

    @Pattern(regexp = "^(pending|confirmed|refused|counter_proposed|cancelled|honored|no_show)$")
    @Column(name = "status", nullable = false)
    private String status = "pending";

    @Column(name = "notes")
    private String notes;

    protected Reservation() {
        // JPA
    }

    public Reservation(UUID id, Tenant tenant, User client, Restaurant restaurant,
                       Instant reservationAt, Integer guestCount) {
        this.id = id;
        this.tenant = tenant;
        this.client = client;
        this.restaurant = restaurant;
        this.reservationAt = reservationAt;
        this.guestCount = guestCount;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Tenant getTenant() { return tenant; }
    public UUID getClientId() { return clientId; }
    public User getClient() { return client; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public UUID getTableId() { return tableId; }
    public RestaurantTable getTable() { return table; }
    public void setTable(RestaurantTable table) { this.table = table; }
    public UUID getServiceId() { return serviceId; }
    public RestaurantService getService() { return service; }
    public void setService(RestaurantService service) { this.service = service; }
    public Instant getReservationAt() { return reservationAt; }
    public void setReservationAt(Instant reservationAt) { this.reservationAt = reservationAt; }
    public Integer getGuestCount() { return guestCount; }
    public void setGuestCount(Integer guestCount) { this.guestCount = guestCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        Reservation that = (Reservation) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
