package com.onesley.oneclick.entity.pcc;

import com.onesley.oneclick.audit.AuditedEntity;
import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.shared.ResourceBookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.resource_bookings} — réservations PCC sur ressources
 * (workflow demandee → confirmee → honoree/no_show/annulee).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code resource_id NOT NULL} → {@link BookableResource} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code organizer_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code invitees jsonb} : tableau de tags d'invités (prénom + nom optionnel +
 *       email optionnel) — pas de FK vers Profile car invités ne sont pas requis
 *       d'avoir un compte (cf. PCC scope decisions).</li>
 * </ul>
 */
@Entity
@Table(name = "resource_bookings")
public class ResourceBooking extends AuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "resource_id", nullable = false, insertable = false, updatable = false)
    private UUID resourceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private BookableResource resource;

    @Column(name = "organizer_id", nullable = false, insertable = false, updatable = false)
    private UUID organizerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private Profile organizer;

    @NotNull
    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @NotNull
    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @NotNull
    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    @JdbcTypeCode(SqlTypes.JSON)
    @NotNull
    @Column(name = "invitees", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> invitees = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @NotNull
    @Column(name = "status", nullable = false, columnDefinition = "resource_booking_status")
    private ResourceBookingStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pricing_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> pricingSnapshot = new HashMap<>();

    @Column(name = "notes")
    private String notes;

    @Column(name = "reminder_j1_sent_at")
    private Instant reminderJ1SentAt;

    @Column(name = "reminder_h2_sent_at")
    private Instant reminderH2SentAt;

    protected ResourceBooking() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getResourceId() { return resourceId; }
    public BookableResource getResource() { return resource; }
    public void setResource(BookableResource resource) { this.resource = resource; }
    public UUID getOrganizerId() { return organizerId; }
    public Profile getOrganizer() { return organizer; }
    public void setOrganizer(Profile organizer) { this.organizer = organizer; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public Integer getPartySize() { return partySize; }
    public Map<String, Object> getInvitees() { return invitees; }
    public ResourceBookingStatus getStatus() { return status; }
    public Map<String, Object> getPricingSnapshot() { return pricingSnapshot; }
    public String getNotes() { return notes; }
    public Instant getReminderJ1SentAt() { return reminderJ1SentAt; }
    public Instant getReminderH2SentAt() { return reminderH2SentAt; }

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
        ResourceBooking that = (ResourceBooking) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
