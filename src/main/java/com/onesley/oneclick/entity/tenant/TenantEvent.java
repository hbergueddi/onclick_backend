package com.onesley.oneclick.entity.tenant;

import com.onesley.oneclick.audit.TimestampedEntity;
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

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.tenant_events} — événements visibles dans l'app par tenant.
 *
 * <p>Pattern : created_at + updated_at hérités.
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code tenant_id NOT NULL} → {@link Tenant} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>Côté inverse {@code @OneToMany rsvps} (EventRsvp) : <b>non ajouté</b> —
 *       volume potentiellement élevé (~100 par event), repository paginé à la place.</li>
 * </ul>
 */
@Entity
@Table(name = "tenant_events")
public class TenantEvent extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // ─── Jointure tenant_id ─────────────────────────────────────────────────
    @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotBlank
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "category")
    private String category;

    @NotNull
    @Column(name = "event_date", nullable = false)
    private Instant eventDate;

    @Column(name = "event_end_date")
    private Instant eventEndDate;

    @Column(name = "capacity")
    private Integer capacity;

    @NotNull
    @Column(name = "rsvp_enabled", nullable = false)
    private Boolean rsvpEnabled;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

    @NotNull
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "visible_until")
    private Instant visibleUntil;

    protected TenantEvent() {
        // JPA
    }

    public UUID getId() { return id; }

    /** Raccourci read-only (issu de la colonne FK). */
    public UUID getTenantId() { return tenantId; }
    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getPhotoUrl() { return photoUrl; }
    public String getCategory() { return category; }
    public Instant getEventDate() { return eventDate; }
    public Instant getEventEndDate() { return eventEndDate; }
    public Integer getCapacity() { return capacity; }
    public Boolean getRsvpEnabled() { return rsvpEnabled; }
    public String getStatus() { return status; }
    public Integer getDisplayOrder() { return displayOrder; }
    public Instant getVisibleUntil() { return visibleUntil; }

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
        TenantEvent that = (TenantEvent) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
