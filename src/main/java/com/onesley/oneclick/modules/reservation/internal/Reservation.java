package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.modules.reservation.api.ReservationDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Réservation — workflow 7 statuts. {@code reservation_at} timestamptz UNIFIÉ.
 *
 * <p>Refonte vs legacy : avant on avait {@code date} (LocalDate) + {@code heure} (String "HH:MM").
 * Maintenant {@code reservation_at} (Instant timestamptz UTC) — query/sort/index plus simples,
 * gestion timezone explicite côté serveur.
 */
@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "table_id")
    @Setter private UUID tableId;

    @Column(name = "service_id")
    @Setter private UUID serviceId;

    @Column(name = "reservation_at", nullable = false)
    @Setter private Instant reservationAt;

    @Column(name = "guest_count", nullable = false)
    @Setter private Integer guestCount;

    @Column(name = "status", nullable = false, length = 64)
    @Setter private String status = "pending";

    @Column(name = "notes", length = 1024)
    @Setter private String notes;

    /**
     * Feature #4 — true si le passage en {@code no_show} fait suite à une annulation
     * tardive (le client a prévenu trop tard). Rend la résa NON contestable et la
     * pénalité non reversable. Défaut false.
     */
    @Column(name = "late_cancellation", nullable = false)
    @Setter private boolean lateCancellation = false;

    /**
     * Feature #3/#4 — horodatage du passage {@code status → no_show}. Base de calcul
     * des fenêtres de contestation (resto 0-1h, support 1-48h, expiré 48h+). NULL tant
     * que la résa n'est pas marquée absente.
     */
    @Column(name = "no_show_marked_at")
    @Setter private Instant noShowMarkedAt;

    public Reservation(UUID id, Tenant tenant, User client, UUID restaurantId,
                       Instant reservationAt, Integer guestCount) {
        this.id = id;
        this.tenant = tenant;
        this.client = client;
        this.restaurantId = restaurantId;
        this.reservationAt = reservationAt;
        this.guestCount = guestCount;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public ReservationDto toDto() {
        return new ReservationDto(id, tenantId, clientId, restaurantId, tableId, serviceId,
            reservationAt, guestCount, status, notes, getCreatedAt(), lateCancellation, noShowMarkedAt);
    }

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
