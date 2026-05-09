package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.audit.CreatedAuditedEntity;
import com.onesley.oneclick.entity.status.EntityStatus;
import com.onesley.oneclick.entity.reservation.Reservation;
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
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.scanned_tickets} — tickets scannés via Snap2Earn (OCR).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code client_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code restaurant_id NOT NULL} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code reservation_id} → {@link Reservation} en {@code @ManyToOne(LAZY)}, nullable
 *       (un ticket peut être scanné sans réservation associée).</li>
 *   <li>{@code scanned_by} / {@code created_by} / {@code modified_by} : audit fields, restent UUID brut.</li>
 * </ul>
 *
 * <p><b>Bug connu Phase 11</b> : la colonne {@code items jsonb} contient en réalité
 * un array JSONB (pas un objet) côté DB. Le mapping {@code Map<String, Object>}
 * échoue → 500 sur GET /api/scanned-tickets. Documenté pour fix future passe.
 */
@Entity
@Table(name = "scanned_tickets")
public class ScannedTicket extends CreatedAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "client_id", nullable = false, insertable = false, updatable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Profile client;

    /** Audit field : UUID brut. Identifie le staff qui a effectué le scan. */
    @NotNull
    @Column(name = "scanned_by", nullable = false)
    private UUID scannedBy;

    @NotBlank
    @Column(name = "ticket_ref", nullable = false)
    private String ticketRef;

    @NotNull
    @Column(name = "montant", nullable = false)
    private BigDecimal montant;

    @NotNull
    @Column(name = "points_credites", nullable = false)
    private Integer pointsCredites;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items", columnDefinition = "jsonb")
    private Map<String, Object> items = new HashMap<>();

    @Column(name = "status_id", nullable = false, insertable = false, updatable = false)
    private UUID statusId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private EntityStatus status;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "reservation_id", insertable = false, updatable = false)
    private UUID reservationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    protected ScannedTicket() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
    public UUID getClientId() { return clientId; }
    public Profile getClient() { return client; }
    public void setClient(Profile client) { this.client = client; }
    public UUID getScannedBy() { return scannedBy; }
    public String getTicketRef() { return ticketRef; }
    public BigDecimal getMontant() { return montant; }
    public Integer getPointsCredites() { return pointsCredites; }
    public Map<String, Object> getItems() { return items; }
    public UUID getStatusId() { return statusId; }
    public EntityStatus getStatus() { return status; }
    public void setStatus(EntityStatus status) { this.status = status; }
    public String getPhotoUrl() { return photoUrl; }
    public UUID getReservationId() { return reservationId; }
    public Reservation getReservation() { return reservation; }
    public void setReservation(Reservation reservation) { this.reservation = reservation; }

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
        ScannedTicket that = (ScannedTicket) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
