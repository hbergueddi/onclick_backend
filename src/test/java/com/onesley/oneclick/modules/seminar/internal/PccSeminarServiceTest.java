package com.onesley.oneclick.modules.seminar.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.seminar.api.SeminarDtos.CreateSeminarRequestDto;
import com.onesley.oneclick.modules.seminar.api.SeminarDtos.SeminarRequestDto;
import com.onesley.oneclick.modules.seminar.api.SeminarDtos.UpdateSeminarStatusDto;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.SeminarRequestedEvent;
import com.onesley.oneclick.shared.events.SeminarStatusChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link PccSeminarService} (L3 — modules.seminar, PCC).
 *
 * <p>Couvre le port legacy + l'ABAC : create (organizer/tenant du caller, pas de tenant → 400,
 * dates incohérentes → 400, participants &lt; 1 → 400, event + STOMP publiés), listMine (self-scope,
 * notes masquées), listForStaff (non-staff → mine ; staff → inbox tenant avec notes), updateStatus
 * (non-staff → 403, admin OK + event/STOMP + bypass tenant, staff autre tenant → 404, statut
 * invalide → 400).</p>
 *
 * <p>{@link SecurityHelper} (statique) simulé pour toute la classe (même pattern que
 * {@code PccFeedbackServiceTest}). Caller = {@link #caller}.</p>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class PccSeminarServiceTest {

    @Mock SeminarRequestRepository repo;
    @Mock UserDirectoryApi userDirectory;
    @Mock MembershipDirectoryApi membershipDirectory;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock SeminarPublisher seminarPublisher;
    @InjectMocks PccSeminarService service;

    private MockedStatic<SecurityHelper> securityMock;

    private final UUID caller = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setup() {
        securityMock = mockStatic(SecurityHelper.class);
        securityMock.when(SecurityHelper::currentUserId).thenReturn(caller);
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(false);
        securityMock.when(SecurityHelper::isAdmin).thenReturn(false);
        lenient().when(userDirectory.tenantIdById(caller)).thenReturn(Optional.of(tenant));
        lenient().when(userDirectory.nameById(any())).thenReturn(Optional.empty());
        // Pas de membership active par défaut → callerProgramTenant retombe sur le home tenant
        // (comportement rétro-compatible attendu par les assertions existantes).
        lenient().when(membershipDirectory.activeTenantIds(any())).thenReturn(List.of());
        lenient().when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(repo.findSeminarRecipientIds(any(), any())).thenReturn(List.of(UUID.randomUUID()));
    }

    @AfterEach
    void tearDown() {
        if (securityMock != null) securityMock.close();
    }

    private CreateSeminarRequestDto validCreate() {
        return new CreateSeminarRequestDto("Atlas Conseil", "Karim Benali", "karim@atlas.ma",
            "+212600112233", 40, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), "Salle plénière + déjeuner");
    }

    private SeminarRequest entity(UUID id, UUID tenantId, UUID organizer) {
        return new SeminarRequest(id, tenantId, organizer, "Atlas Conseil", "Karim Benali",
            "karim@atlas.ma", "+212600112233", 40, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), "besoins");
    }

    // ─── create ─────────────────────────────────────────────────────────────────

    @Test
    void create_persistsAndPublishes() {
        SeminarRequestDto dto = service.create(validCreate());

        assertThat(dto.organizerId()).isEqualTo(caller);
        assertThat(dto.tenantId()).isEqualTo(tenant);
        assertThat(dto.companyName()).isEqualTo("Atlas Conseil");
        assertThat(dto.status()).isEqualTo("demandee");
        assertThat(dto.notesInternal()).as("notes internes masquées côté membre").isNull();

        verify(repo).save(any(SeminarRequest.class));
        verify(eventPublisher).publishEvent(any(SeminarRequestedEvent.class));
        verify(seminarPublisher).publishToInbox(any(SeminarRequestDto.class));
    }

    @Test
    void create_noTenant_throws400() {
        when(userDirectory.tenantIdById(caller)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(validCreate()))
            .isInstanceOf(BadRequestException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_endBeforeStart_throws400() {
        CreateSeminarRequestDto bad = new CreateSeminarRequestDto("Atlas", "Karim", "k@a.ma", null, 10,
            LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 1), null);
        assertThatThrownBy(() -> service.create(bad)).isInstanceOf(BadRequestException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_attendeesBelowOne_throws400() {
        CreateSeminarRequestDto bad = new CreateSeminarRequestDto("Atlas", "Karim", "k@a.ma", null, 0,
            null, null, null);
        assertThatThrownBy(() -> service.create(bad)).isInstanceOf(BadRequestException.class);
        verify(repo, never()).save(any());
    }

    // ─── listMine ─────────────────────────────────────────────────────────────────

    @Test
    void listMine_selfScope_hidesInternalNotes() {
        SeminarRequest s = entity(UUID.randomUUID(), tenant, caller);
        s.applyStatus("en_traitement", "Note interne confidentielle");
        when(repo.findByOrganizerIdOrderByCreatedAtDesc(caller)).thenReturn(List.of(s));

        List<SeminarRequestDto> out = service.listMine();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).status()).isEqualTo("en_traitement");
        assertThat(out.get(0).notesInternal()).as("notes internes jamais exposées au membre").isNull();
    }

    // ─── listForStaff ───────────────────────────────────────────────────────────

    @Test
    void listForStaff_nonStaff_fallsBackToMine() {
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(false);
        when(repo.findByOrganizerIdOrderByCreatedAtDesc(caller)).thenReturn(List.of());

        service.listForStaff();

        verify(repo).findByOrganizerIdOrderByCreatedAtDesc(caller);
        verify(repo, never()).findByTenantIdOrderByCreatedAtDesc(any());
    }

    @Test
    void listForStaff_staff_returnsTenantInboxWithNotes() {
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
        SeminarRequest s = entity(UUID.randomUUID(), tenant, UUID.randomUUID());
        s.applyStatus("devis_envoye", "Devis 35k MAD envoyé");
        when(repo.findByTenantIdOrderByCreatedAtDesc(tenant)).thenReturn(List.of(s));

        List<SeminarRequestDto> out = service.listForStaff();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).notesInternal()).as("notes internes visibles côté commercial")
            .isEqualTo("Devis 35k MAD envoyé");
    }

    // ─── updateStatus ───────────────────────────────────────────────────────────

    @Test
    void updateStatus_nonStaff_throws403() {
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(false);
        assertThatThrownBy(() -> service.updateStatus(UUID.randomUUID(),
            new UpdateSeminarStatusDto("confirmee", null)))
            .isInstanceOf(ForbiddenException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void updateStatus_admin_bypassesTenant_andPublishes() {
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
        securityMock.when(SecurityHelper::isAdmin).thenReturn(true);
        UUID id = UUID.randomUUID();
        UUID organizer = UUID.randomUUID();
        // Demande d'un AUTRE tenant : l'admin doit pouvoir la traiter (bypass).
        when(repo.findById(id)).thenReturn(Optional.of(entity(id, UUID.randomUUID(), organizer)));

        SeminarRequestDto dto = service.updateStatus(id, new UpdateSeminarStatusDto("confirmee", "OK go"));

        assertThat(dto.status()).isEqualTo("confirmee");
        assertThat(dto.notesInternal()).isEqualTo("OK go");
        verify(repo).save(any(SeminarRequest.class));
        verify(eventPublisher).publishEvent(any(SeminarStatusChangedEvent.class));
        verify(seminarPublisher).publishToInbox(any(SeminarRequestDto.class));
        verify(seminarPublisher).publishToOrganizer(any(), any(SeminarRequestDto.class));
    }

    @Test
    void updateStatus_staffWrongTenant_throws404() {
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
        securityMock.when(SecurityHelper::isAdmin).thenReturn(false);
        UUID id = UUID.randomUUID();
        // caller a pour tenant `tenant`, mais la demande est d'un autre tenant → 404 (isolation).
        when(repo.findById(id)).thenReturn(Optional.of(entity(id, UUID.randomUUID(), UUID.randomUUID())));

        assertThatThrownBy(() -> service.updateStatus(id, new UpdateSeminarStatusDto("confirmee", null)))
            .isInstanceOf(NotFoundException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void updateStatus_invalidStatus_throws400() {
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
        assertThatThrownBy(() -> service.updateStatus(UUID.randomUUID(),
            new UpdateSeminarStatusDto("bogus_status", null)))
            .isInstanceOf(BadRequestException.class);
        verify(repo, never()).findById(any());
    }
}
