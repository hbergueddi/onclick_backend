package com.onesley.oneclick.entity.reservation;

import com.onesley.oneclick.entity.shared.ReservationStatus;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entité {@code public.reservations} — workflow réservation (couverts).
 *
 * <p>Pattern pilote : <i>enum DB + 2 FKs + LocalDate + workflow horizontal</i>.
 *
 * <p>Mapping notable :
 * <ul>
 *   <li>{@code status reservation_status} → enum Java {@link ReservationStatus}
 *       avec {@code @JdbcTypeCode(NAMED_ENUM)} pour binding natif PG</li>
 *   <li>{@code date date} → {@link LocalDate} (pas {@link java.time.Instant} !)</li>
 *   <li>{@code heure text} → {@link String} (convention "HH:MM" préservée du
 *       frontend, pas {@link java.time.LocalTime})</li>
 *   <li>{@code client_id} + {@code restaurant_id} → UUID brut (FK matérialisable
 *       à la demande mais pas par défaut, voir doc Profile)</li>
 *   <li>10 colonnes workflow (refusal_reason, proposed_*, no_show_*, reminder_*) :
 *       toutes nullables, pilotées par les transitions d'états</li>
 * </ul>
 *
 * <p>Note : Hibernate ne valide PAS la transition d'état au niveau entité.
 * Les machines d'états sont gérées dans le service métier (Phase 11) ou via
 * triggers DB existants.
 */
@Entity
@Table(name = "reservations")
public class Reservation extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

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

    protected Reservation() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public UUID getRestaurantId() { return restaurantId; }
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
}
