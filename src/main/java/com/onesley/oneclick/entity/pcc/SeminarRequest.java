package com.onesley.oneclick.entity.pcc;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.seminar_requests} — demandes B2B de séminaires PCC
 * (workflow devis → confirmation manuelle commercial).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code tenant_id} → {@link Tenant} en {@code @ManyToOne(LAZY)}, nullable.</li>
 *   <li>{@code organizer_id} → {@link Profile} en {@code @ManyToOne(LAZY)}, nullable
 *       (la demande peut être faite par un anonymous via form public).</li>
 * </ul>
 */
@Entity
@Table(name = "seminar_requests")
public class SeminarRequest extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "organizer_id", insertable = false, updatable = false)
    private UUID organizerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id")
    private Profile organizer;

    @NotBlank
    @Column(name = "company_name", nullable = false)
    private String companyName;

    @NotBlank
    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Email
    @NotBlank
    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @NotNull
    @Column(name = "expected_attendees", nullable = false)
    private Integer expectedAttendees;

    @Column(name = "preferred_date_start")
    private LocalDate preferredDateStart;

    @Column(name = "preferred_date_end")
    private LocalDate preferredDateEnd;

    @Column(name = "needs_text")
    private String needsText;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "notes_internal")
    private String notesInternal;

    protected SeminarRequest() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public UUID getOrganizerId() { return organizerId; }
    public Profile getOrganizer() { return organizer; }
    public void setOrganizer(Profile organizer) { this.organizer = organizer; }
    public String getCompanyName() { return companyName; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public Integer getExpectedAttendees() { return expectedAttendees; }
    public LocalDate getPreferredDateStart() { return preferredDateStart; }
    public LocalDate getPreferredDateEnd() { return preferredDateEnd; }
    public String getNeedsText() { return needsText; }
    public String getStatus() { return status; }
    public String getNotesInternal() { return notesInternal; }

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
        SeminarRequest that = (SeminarRequest) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
