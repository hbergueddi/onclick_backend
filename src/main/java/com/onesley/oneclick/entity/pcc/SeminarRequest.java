package com.onesley.oneclick.entity.pcc;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entité {@code public.seminar_requests} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "seminar_requests")
public class SeminarRequest extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "organizer_id")
    private UUID organizerId;

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
    public UUID getOrganizerId() { return organizerId; }
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
}
