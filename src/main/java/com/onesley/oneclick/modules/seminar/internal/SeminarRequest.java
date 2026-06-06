package com.onesley.oneclick.modules.seminar.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Demande de devis « Séminaire » B2B d'un membre (PCC) — workflow de statut
 * {@code demandee → en_traitement → devis_envoye → confirmee | refusee | annulee}.
 *
 * <p>Le membre ({@code organizerId}) soumet une demande (entreprise / contact / nombre de
 * participants / dates souhaitées / besoins). Le commercial (staff/admin du tenant) traite la
 * demande en faisant évoluer {@code status} et en consignant des {@code notesInternal}. À chaque
 * changement de statut, l'organisateur est notifié.
 *
 * <p>Table {@code seminar_requests} (V73). {@code tenantId} = tenant du membre, figé à la création
 * (scope de l'inbox commercial + isolation cross-tenant). Les champs « demande » (entreprise,
 * contact, participants, dates, besoins) sont immuables après création ({@code updatable=false}) :
 * seul le couple {@code status} / {@code notesInternal} évolue (port fidèle du RPC legacy
 * {@code update_seminar_status} qui ne modifiait que ces deux colonnes). Le nom/contact de
 * l'organisateur est résolu à la lecture (UserDirectoryApi), jamais dénormalisé ici.
 */
@Entity
@Table(name = "seminar_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeminarRequest extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Tenant du membre — scope de l'inbox commercial + isolation cross-tenant. */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Membre organisateur (le caller qui soumet). Peut devenir null si le compte est supprimé. */
    @Column(name = "organizer_id", updatable = false)
    private UUID organizerId;

    @Column(name = "company_name", nullable = false, length = 255, updatable = false)
    private String companyName;

    @Column(name = "contact_name", nullable = false, length = 255, updatable = false)
    private String contactName;

    @Column(name = "contact_email", nullable = false, length = 320, updatable = false)
    private String contactEmail;

    @Column(name = "contact_phone", length = 40, updatable = false)
    private String contactPhone;

    /** Nombre de participants attendus (CHECK >= 1 en base), figé à la demande. */
    @Column(name = "expected_attendees", nullable = false, updatable = false)
    private int expectedAttendees;

    @Column(name = "preferred_date_start", updatable = false)
    private LocalDate preferredDateStart;

    @Column(name = "preferred_date_end", updatable = false)
    private LocalDate preferredDateEnd;

    /** Description libre des besoins (≤ 4000), figée à la demande. */
    @Column(name = "needs_text", length = 4000, updatable = false)
    private String needsText;

    /** Statut du workflow (CHECK en base) — mutable par le commercial. */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /** Notes internes commercial (≤ 4000) — mutable, non exposées au membre dans l'UI. */
    @Column(name = "notes_internal", length = 4000)
    private String notesInternal;

    /** Création d'une demande (statut initial {@code demandee}). */
    public SeminarRequest(UUID id, UUID tenantId, UUID organizerId, String companyName,
                          String contactName, String contactEmail, String contactPhone,
                          int expectedAttendees, LocalDate preferredDateStart,
                          LocalDate preferredDateEnd, String needsText) {
        this.id = id;
        this.tenantId = tenantId;
        this.organizerId = organizerId;
        this.companyName = companyName;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.expectedAttendees = expectedAttendees;
        this.preferredDateStart = preferredDateStart;
        this.preferredDateEnd = preferredDateEnd;
        this.needsText = needsText;
        this.status = "demandee";
    }

    /**
     * Applique une transition de statut par le commercial. {@code notesInternal} est mis à jour
     * uniquement s'il est fourni (non null) — un changement de statut sans note ne l'écrase pas.
     */
    public void applyStatus(String newStatus, String notesInternal) {
        this.status = newStatus;
        if (notesInternal != null) {
            this.notesInternal = notesInternal;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((SeminarRequest) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
