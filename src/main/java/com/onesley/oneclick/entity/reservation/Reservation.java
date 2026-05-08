package com.onesley.oneclick.entity.reservation;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.restaurant.Restaurant;
import com.onesley.oneclick.entity.shared.ReservationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Entité {@code public.reservations} — workflow réservation (couverts).
 *
 * <p>Pattern : <i>enum DB + 2 FKs + LocalDate + workflow horizontal +
 * aggregate root pour {@link ReservationGuest}</i>.
 *
 * <p>Mapping notable :
 * <ul>
 *   <li>{@code status reservation_status} → enum Java {@link ReservationStatus}
 *       avec {@code @JdbcTypeCode(NAMED_ENUM)} pour binding natif PG</li>
 *   <li>{@code date date} → {@link LocalDate} (pas {@link java.time.Instant} !)</li>
 *   <li>{@code heure text} → {@link String} (convention "HH:MM" préservée du
 *       frontend, pas {@link java.time.LocalTime})</li>
 *   <li>10 colonnes workflow (refusal_reason, proposed_*, no_show_*, reminder_*) :
 *       toutes nullables, pilotées par les transitions d'états</li>
 * </ul>
 *
 * <p>Note : Hibernate ne valide PAS la transition d'état au niveau entité.
 * Les machines d'états sont gérées dans le service métier (Phase 11) ou via
 * triggers DB existants.
 *
 * <h3>Jointures JPA (passe 3) — aggregate root</h3>
 * <ul>
 *   <li>{@code client_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code restaurant_id NOT NULL} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code @OneToMany guests} : aggregate fort, cascade ALL + orphanRemoval +
 *       {@code @BatchSize(50)}. Helpers {@link #addGuest} / {@link #removeGuest}
 *       pour cohérence bidirectionnelle.</li>
 * </ul>
 */
@Entity
@Table(name = "reservations")
public class Reservation extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // ─── Jointure client_id ─────────────────────────────────────────────────
    @Column(name = "client_id", nullable = false, insertable = false, updatable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Profile client;

    // ─── Jointure restaurant_id ─────────────────────────────────────────────
    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "heure", nullable = false)
    private String heure;

    @Column(name = "couverts", nullable = false)
    private Integer couverts;

    @Column(name = "zone")
    private String zone;

    @Column(name = "table_num")
    private Integer tableNum;

    @Column(name = "service", nullable = false)
    private String service;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "reservation_status")
    private ReservationStatus status;

    @Column(name = "notes")
    private String notes;

    @Column(name = "refusal_reason")
    private String refusalReason;

    @Column(name = "proposed_date")
    private LocalDate proposedDate;

    @Column(name = "proposed_heure")
    private String proposedHeure;

    @Column(name = "proposal_expires_at")
    private Instant proposalExpiresAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "no_show_marked_at")
    private Instant noShowMarkedAt;

    @Column(name = "no_show_penalty_applied_at")
    private Instant noShowPenaltyAppliedAt;

    // Annulation tardive, doit etre une regle , sinon blocage
    @Column(name = "late_cancellation", nullable = false)
    private Boolean lateCancellation = false;

    @Column(name = "reminder_j1_sent_at")
    private Instant reminderJ1SentAt;

    @Column(name = "reminder_h2_sent_at")
    private Instant reminderH2SentAt;

    // ─── Aggregate member : guests (cascade ALL + orphanRemoval) ────────────
    @OneToMany(mappedBy = "reservation", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private Set<ReservationGuest> guests = new HashSet<>();

    protected Reservation() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public Profile getClient() { return client; }
    public void setClient(Profile client) { this.client = client; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
    public LocalDate getDate() { return date; }
    public String getHeure() { return heure; }
    public Integer getCouverts() { return couverts; }
    public String getZone() { return zone; }
    public Integer getTableNum() { return tableNum; }
    public String getService() { return service; }
    public ReservationStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public String getRefusalReason() { return refusalReason; }
    public LocalDate getProposedDate() { return proposedDate; }
    public String getProposedHeure() { return proposedHeure; }
    public Instant getProposalExpiresAt() { return proposalExpiresAt; }
    public String getCancellationReason() { return cancellationReason; }
    public Instant getNoShowMarkedAt() { return noShowMarkedAt; }
    public Instant getNoShowPenaltyAppliedAt() { return noShowPenaltyAppliedAt; }
    public Boolean getLateCancellation() { return lateCancellation; }
    public Instant getReminderJ1SentAt() { return reminderJ1SentAt; }
    public Instant getReminderH2SentAt() { return reminderH2SentAt; }
    public Set<ReservationGuest> getGuests() { return guests; }

    // ─── Helpers bidirectionnels (cohérence mémoire) ────────────────────────
    public void addGuest(ReservationGuest g) {
        guests.add(g);
        g.setReservation(this);
    }

    public void removeGuest(ReservationGuest g) {
        guests.remove(g);
        g.setReservation(null);  // déclenche orphanRemoval au flush
    }

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
        Reservation that = (Reservation) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
