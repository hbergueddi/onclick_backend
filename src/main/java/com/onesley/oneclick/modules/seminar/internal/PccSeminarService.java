package com.onesley.oneclick.modules.seminar.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi.UserName;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.seminar.api.SeminarDtos.CreateSeminarRequestDto;
import com.onesley.oneclick.modules.seminar.api.SeminarDtos.SeminarRequestDto;
import com.onesley.oneclick.modules.seminar.api.SeminarDtos.UpdateSeminarStatusDto;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.SeminarRequestedEvent;
import com.onesley.oneclick.shared.events.SeminarStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Logique « Séminaires » (PCC) — port fidèle du legacy (RPC {@code create_seminar_request} /
 * {@code update_seminar_status} + Edge Function {@code send-pcc-seminar-status-update}) vers un
 * service Spring avec ABAC.
 *
 * <h3>Mapping legacy → méthode</h3>
 * <ul>
 *   <li>RPC {@code create_seminar_request} (+ notif commercial) → {@link #create}</li>
 *   <li>(lecture membre) → {@link #listMine}</li>
 *   <li>(lecture commercial, RLS staff/admin du tenant) → {@link #listForStaff}</li>
 *   <li>RPC {@code update_seminar_status} (+ EF notif organisateur) → {@link #updateStatus}</li>
 * </ul>
 *
 * <h3>ABAC</h3>
 * <ul>
 *   <li>{@code create} : {@code organizer_id} = caller, {@code tenant_id} = tenant du caller
 *       (UserDirectoryApi) — pas de slug palmeraie en dur.</li>
 *   <li>{@code listMine} : self-scope (caller = organizer_id) ; {@code notesInternal} masqué.</li>
 *   <li>{@code listForStaff} : staff/admin → inbox tenant-wide (toutes les demandes du tenant) ;
 *       un non-staff retombe sur {@link #listMine} (défense en profondeur — VIEW:SEMINARS que le
 *       CLIENT possède aussi).</li>
 *   <li>{@code updateStatus} : staff/admin uniquement (403 sinon). Restreint au tenant du caller
 *       (404 si la demande n'est pas du tenant — pas de fuite cross-tenant).</li>
 * </ul>
 *
 * <h3>Cross-module (Modulith CLOSED)</h3>
 * <p>users → {@code UserDirectoryApi} (core.identity OPEN). destinataires notif → read-view SQL
 * native du repo (tables {@code users}/{@code roles}). Aucun import de {@code modules.restaurant}/
 * {@code modules.loyalty}. Notification → events Modulith consommés par {@code core.notification}.
 * Temps réel → {@link SeminarPublisher} (STOMP).</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class PccSeminarService {

    private static final Set<String> VALID_STATUSES = Set.of(
        "demandee", "en_traitement", "devis_envoye", "confirmee", "refusee", "annulee");

    private final SeminarRequestRepository repo;
    private final UserDirectoryApi userDirectory;
    private final ApplicationEventPublisher eventPublisher;
    private final SeminarPublisher seminarPublisher;

    // ─── create (membre soumet une demande) ─────────────────────────────────────

    /**
     * Crée une demande du caller. {@code organizer_id} = caller, {@code tenant_id} = tenant du
     * caller. Valide les dates (fin ≥ début si les deux fournies). Publie
     * {@link SeminarRequestedEvent} (→ notif commercial) + push STOMP inbox. Retourne le
     * {@link SeminarRequestDto} (sans notes internes — contexte membre).
     */
    @Transactional
    public SeminarRequestDto create(CreateSeminarRequestDto dto) {
        UUID caller = requireCaller();
        UUID tenantId = userDirectory.tenantIdById(caller)
            .orElseThrow(() -> new BadRequestException(
                "Aucun tenant associé à votre compte — les demandes de séminaire ne sont pas disponibles"));

        String companyName = requireText(dto.companyName(), "Nom de l'entreprise requis");
        String contactName = requireText(dto.contactName(), "Nom du contact requis");
        String contactEmail = requireText(dto.contactEmail(), "Email de contact requis");
        if (!contactEmail.contains("@")) {
            throw new BadRequestException("Email de contact invalide");
        }
        int attendees = dto.expectedAttendees() == null ? 0 : dto.expectedAttendees();
        if (attendees < 1) {
            throw new BadRequestException("Le nombre de participants doit être au moins 1");
        }
        if (dto.preferredDateStart() != null && dto.preferredDateEnd() != null
                && dto.preferredDateEnd().isBefore(dto.preferredDateStart())) {
            throw new BadRequestException("La date de fin doit être postérieure ou égale à la date de début");
        }

        SeminarRequest saved = repo.save(new SeminarRequest(
            UUID.randomUUID(), tenantId, caller, companyName, contactName, contactEmail,
            trimToNull(dto.contactPhone()), attendees, dto.preferredDateStart(),
            dto.preferredDateEnd(), trimToNull(dto.needsText())));
        log.info("[seminar] create (id={}, organizer={}, company={}, attendees={})",
            saved.getId(), caller, companyName, attendees);

        SeminarRequestDto out = toDto(saved, false);

        // Notif server-side (le CLIENT n'a pas CREATE:NOTIFICATIONS) → staff/admins du tenant.
        // Destinataires résolus ici (read-view native) et portés sur l'event (frontière Modulith).
        List<UUID> recipients = repo.findSeminarRecipientIds(tenantId, caller);
        eventPublisher.publishEvent(new SeminarRequestedEvent(
            saved.getId(), caller, tenantId, recipients, companyName, Instant.now()));
        // Temps réel : push immédiat sur l'inbox commercial (la version staff porte les notes).
        seminarPublisher.publishToInbox(toDto(saved, true));

        return out;
    }

    // ─── listMine (mes demandes) ────────────────────────────────────────────────

    /** « Mes demandes » du caller (self-scope), du plus récent au plus ancien (sans notes internes). */
    public List<SeminarRequestDto> listMine() {
        UUID caller = requireCaller();
        return repo.findByOrganizerIdOrderByCreatedAtDesc(caller).stream()
            .map(s -> toDto(s, false))
            .toList();
    }

    // ─── listForStaff (inbox commercial tenant-wide) ────────────────────────────

    /**
     * Inbox commercial : staff/admin → toutes les demandes du tenant du caller (avec notes
     * internes) ; un non-staff retombe sur {@link #listMine} (défense en profondeur).
     */
    public List<SeminarRequestDto> listForStaff() {
        UUID caller = requireCaller();
        if (!SecurityHelper.isStaffOrAdmin()) {
            return listMine();
        }
        UUID tenantId = userDirectory.tenantIdById(caller).orElse(null);
        if (tenantId == null) {
            return List.of();
        }
        return repo.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
            .map(s -> toDto(s, true))
            .toList();
    }

    // ─── updateStatus (commercial traite) ───────────────────────────────────────

    /**
     * Change le statut d'une demande (+ notes internes optionnelles). Staff/admin only (403 sinon).
     * Restreint au tenant du caller (404 si hors tenant — pas de fuite cross-tenant). Publie
     * {@link SeminarStatusChangedEvent} (→ notif organisateur, libellé par statut) + push STOMP
     * (inbox commercial + topic du membre).
     */
    @Transactional
    public SeminarRequestDto updateStatus(UUID seminarId, UpdateSeminarStatusDto dto) {
        UUID caller = requireCaller();
        if (!SecurityHelper.isStaffOrAdmin()) {
            throw new ForbiddenException("Accès interdit : seul un commercial (staff/admin) peut traiter une demande");
        }
        String status = dto.status() == null ? null : dto.status().trim();
        if (status == null || !VALID_STATUSES.contains(status)) {
            throw new BadRequestException("Statut invalide");
        }

        SeminarRequest sr = repo.findById(seminarId)
            .orElseThrow(() -> new NotFoundException("SeminarRequest", seminarId));

        // Isolation tenant : un staff NON-admin ne traite que les demandes de SON tenant.
        // L'admin (GROUP_ADMIN/SUPERADMIN) bypasse (peut traiter tout tenant — calque legacy
        // has_role(admin) + pattern feedback reply où l'admin bypasse l'owner-scope). Un
        // SUPERADMIN plateforme n'a pas de tenant : sans ce bypass, son update échouerait à tort.
        if (!SecurityHelper.isAdmin()) {
            UUID callerTenant = userDirectory.tenantIdById(caller).orElse(null);
            if (callerTenant == null || !callerTenant.equals(sr.getTenantId())) {
                // 404 (pas 403) pour ne pas révéler l'existence d'une demande d'un autre tenant.
                throw new NotFoundException("SeminarRequest", seminarId);
            }
        }

        sr.applyStatus(status, trimToNull(dto.notesInternal()));
        SeminarRequest saved = repo.save(sr);
        log.info("[seminar] status (id={}, by={}, status={})", saved.getId(), caller, status);

        SeminarRequestDto staffView = toDto(saved, true);

        // Notif server-side à l'organisateur + push STOMP (inbox commercial + topic membre).
        eventPublisher.publishEvent(new SeminarStatusChangedEvent(
            saved.getId(), saved.getOrganizerId(), caller, status, saved.getCompanyName(), Instant.now()));
        seminarPublisher.publishToInbox(staffView);
        // Le push membre ne porte PAS les notes internes (vue membre).
        seminarPublisher.publishToOrganizer(saved.getOrganizerId(), toDto(saved, false));

        return staffView;
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private UUID requireCaller() {
        UUID caller = SecurityHelper.currentUserId();
        if (caller == null) {
            throw new ForbiddenException("Authentification requise");
        }
        return caller;
    }

    /**
     * Mapping entité → DTO. {@code includeNotes} = true uniquement pour les vues staff/admin
     * (les notes internes ne sont jamais exposées au membre). Enrichit nom/contact de
     * l'organisateur via UserDirectoryApi.
     */
    private SeminarRequestDto toDto(SeminarRequest s, boolean includeNotes) {
        UserName org = s.getOrganizerId() == null
            ? null
            : userDirectory.nameById(s.getOrganizerId()).orElse(null);
        return new SeminarRequestDto(
            s.getId(),
            s.getOrganizerId(),
            org != null ? org.firstName() : null,
            org != null ? org.lastName() : null,
            org != null ? org.email() : null,
            s.getTenantId(),
            s.getCompanyName(),
            s.getContactName(),
            s.getContactEmail(),
            s.getContactPhone(),
            s.getExpectedAttendees(),
            s.getPreferredDateStart(),
            s.getPreferredDateEnd(),
            s.getNeedsText(),
            s.getStatus(),
            includeNotes ? s.getNotesInternal() : null,
            s.getCreatedAt(),
            s.getUpdatedAt());
    }

    private static String requireText(String s, String message) {
        String t = trimToNull(s);
        if (t == null) {
            throw new BadRequestException(message);
        }
        return t;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
