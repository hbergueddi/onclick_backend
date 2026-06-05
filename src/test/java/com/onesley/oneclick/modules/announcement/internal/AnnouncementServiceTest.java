package com.onesley.oneclick.modules.announcement.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.announcement.api.AnnouncementDtos.AnnouncementDto;
import com.onesley.oneclick.modules.announcement.api.AnnouncementDtos.CreateAnnouncementDto;
import com.onesley.oneclick.modules.announcement.api.AnnouncementDtos.UpdateAnnouncementDto;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.AnnouncementPublishedEvent;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link AnnouncementService} (L3 — modules.announcement, Lot 8).
 *
 * <p>Couvre l'ABAC + les règles métier portées par le service (ex-triggers SQL) :
 * <ul>
 *   <li><b>pinned-enforce</b> : créer une annonce épinglée appelle {@code unpinOthers}.</li>
 *   <li><b>body_version bump + reset reads</b> : éditer le corps bump {@code bodyVersion} ET purge
 *       les acquittements ({@code deleteByAnnouncementId}).</li>
 *   <li><b>mark-read self</b> : le staff du tenant acquitte (upsert) ; un user hors tenant → 403.</li>
 *   <li><b>write admin-only</b> : create/update/archive/delete par un non-admin du tenant → 403.</li>
 *   <li>listForMe (admin → all, staff → published, ni l'un ni l'autre → vide) ; create sans tenant
 *       → 400 ; notif/STOMP publiés seulement si publiée.</li>
 * </ul>
 *
 * <p>{@link SecurityHelper} (statique) simulé pour toute la classe (même pattern que
 * {@code PccFeedbackServiceTest}). Caller = {@link #caller}.</p>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class AnnouncementServiceTest {

    @Mock AnnouncementRepository repo;
    @Mock AnnouncementReadRepository readRepo;
    @Mock UserDirectoryApi userDirectory;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AnnouncementPublisher announcementPublisher;
    @InjectMocks AnnouncementService service;

    private MockedStatic<SecurityHelper> securityMock;

    private final UUID caller = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setup() {
        securityMock = mockStatic(SecurityHelper.class);
        securityMock.when(SecurityHelper::currentUserId).thenReturn(caller);
        securityMock.when(SecurityHelper::isAdmin).thenReturn(false);
        lenient().when(userDirectory.tenantIdById(caller)).thenReturn(Optional.of(tenant));
        lenient().when(userDirectory.nameById(any())).thenReturn(Optional.empty());
        lenient().when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(repo.findStaffRecipientIds(any(), any())).thenReturn(List.of(UUID.randomUUID()));
        lenient().when(readRepo.findReadAnnouncementIdsForUser(any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        if (securityMock != null) securityMock.close();
    }

    private Announcement announcement(boolean pinned, Instant publishAt) {
        return new Announcement(UUID.randomUUID(), tenant, caller, "Titre", "Corps", null,
            "permanent", pinned, publishAt);
    }

    // ─── create : write admin-only + pinned-enforce + publish gating ──────────────

    @Test
    void create_byNonTenantAdmin_forbidden() {
        when(repo.isTenantAdmin(caller, tenant)).thenReturn(false);
        assertThatThrownBy(() -> service.create(new CreateAnnouncementDto(
            "Titre", "Corps", null, "permanent", true, null)))
            .isInstanceOf(ForbiddenException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_noTenant_throws400() {
        when(userDirectory.tenantIdById(caller)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(new CreateAnnouncementDto(
            "Titre", "Corps", null, "permanent", true, null)))
            .isInstanceOf(BadRequestException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_pinned_enforcesMaxPinned_andPublishesEventAndStomp() {
        when(repo.isTenantAdmin(caller, tenant)).thenReturn(true);

        AnnouncementDto out = service.create(new CreateAnnouncementDto(
            "Horaires été", "Le club ouvre à 7h", null, "urgent", true, null)); // publishAt null → now → publiée

        assertThat(out.tenantId()).isEqualTo(tenant);
        assertThat(out.authorId()).isEqualTo(caller);
        assertThat(out.priority()).isEqualTo("urgent");
        assertThat(out.pinned()).isTrue();
        verify(repo).save(any(Announcement.class));
        // pinned → désépinglage des autres de la même priorité/tenant.
        verify(repo).unpinOthers(eq(tenant), eq("urgent"), any(UUID.class));
        // publiée → event notif staff + push STOMP.
        verify(eventPublisher).publishEvent(any(AnnouncementPublishedEvent.class));
        verify(announcementPublisher).publish(eq(tenant), any(AnnouncementDto.class));
    }

    @Test
    void create_notPinned_doesNotEnforceMaxPinned() {
        when(repo.isTenantAdmin(caller, tenant)).thenReturn(true);
        service.create(new CreateAnnouncementDto("T", "B", null, "permanent", false, null));
        verify(repo, never()).unpinOthers(any(), any(), any());
    }

    @Test
    void create_scheduledFuture_doesNotNotify() {
        when(repo.isTenantAdmin(caller, tenant)).thenReturn(true);
        Instant future = Instant.now().plus(2, ChronoUnit.DAYS);
        AnnouncementDto out = service.create(new CreateAnnouncementDto(
            "Programmée", "Plus tard", null, "permanent", true, future));
        assertThat(out.published()).isFalse();
        // Programmée → pas de notif staff ni de push (push différé = V1 hors scope).
        verify(eventPublisher, never()).publishEvent(any());
        verify(announcementPublisher, never()).publish(any(), any());
    }

    @Test
    void create_invalidPriority_throws400() {
        when(repo.isTenantAdmin(caller, tenant)).thenReturn(true);
        // Le service valide aussi la priorité (défense en profondeur vs Bean Validation).
        assertThatThrownBy(() -> service.create(new CreateAnnouncementDto(
            "T", "B", null, "weird", true, null)))
            .isInstanceOf(BadRequestException.class);
    }

    // ─── update : body_version bump + reset reads ────────────────────────────────

    @Test
    void update_bodyChanged_bumpsVersion_andResetsReads() {
        securityMock.when(SecurityHelper::isAdmin).thenReturn(true); // admin global → bypass tenant-admin
        UUID id = UUID.randomUUID();
        Announcement existing = announcement(true, Instant.now().minus(1, ChronoUnit.HOURS));
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        AnnouncementDto out = service.update(id, new UpdateAnnouncementDto(
            "Titre", "Corps MODIFIÉ", null, "permanent", true, null));

        assertThat(out.bodyVersion()).isEqualTo(2); // 1 → 2
        verify(readRepo).deleteByAnnouncementId(id); // reset des acquittements
        // publiée + body changé → re-notif staff.
        verify(eventPublisher).publishEvent(any(AnnouncementPublishedEvent.class));
    }

    @Test
    void update_bodyUnchanged_keepsVersion_andDoesNotResetReads() {
        securityMock.when(SecurityHelper::isAdmin).thenReturn(true);
        UUID id = UUID.randomUUID();
        Announcement existing = announcement(true, Instant.now().minus(1, ChronoUnit.HOURS));
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        AnnouncementDto out = service.update(id, new UpdateAnnouncementDto(
            "Titre RENOMMÉ", "Corps", null, "permanent", true, null)); // body identique

        assertThat(out.bodyVersion()).isEqualTo(1);
        verify(readRepo, never()).deleteByAnnouncementId(any());
        // pas de nouvelle version → pas de re-notif, mais push live (édition mineure).
        verify(eventPublisher, never()).publishEvent(any());
        verify(announcementPublisher).publish(eq(tenant), any(AnnouncementDto.class));
    }

    @Test
    void update_byNonAdmin_forbidden() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(announcement(true, Instant.now())));
        when(repo.isTenantAdmin(caller, tenant)).thenReturn(false);
        assertThatThrownBy(() -> service.update(id, new UpdateAnnouncementDto(
            "T", "B", null, "permanent", true, null)))
            .isInstanceOf(ForbiddenException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void update_notFound_throws404() {
        securityMock.when(SecurityHelper::isAdmin).thenReturn(true);
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(id, new UpdateAnnouncementDto(
            "T", "B", null, "permanent", true, null)))
            .isInstanceOf(NotFoundException.class);
    }

    // ─── markRead : self / tenant-scope ──────────────────────────────────────────

    @Test
    void markRead_byTenantStaff_upserts() {
        UUID id = UUID.randomUUID();
        Announcement a = announcement(false, Instant.now().minus(1, ChronoUnit.HOURS));
        when(repo.findById(id)).thenReturn(Optional.of(a));
        when(repo.isTenantAdmin(caller, tenant)).thenReturn(false);
        when(repo.isActiveStaffOfTenant(caller, tenant)).thenReturn(true);
        when(readRepo.findByAnnouncementIdAndUserId(id, caller)).thenReturn(Optional.empty());

        service.markRead(id, 1);

        verify(readRepo).save(any(AnnouncementRead.class));
    }

    @Test
    void markRead_byUserOutsideTenant_forbidden() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(announcement(false, Instant.now())));
        when(repo.isTenantAdmin(caller, tenant)).thenReturn(false);
        when(repo.isActiveStaffOfTenant(caller, tenant)).thenReturn(false);
        assertThatThrownBy(() -> service.markRead(id, 1))
            .isInstanceOf(ForbiddenException.class);
        verify(readRepo, never()).save(any());
    }

    @Test
    void markRead_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markRead(id, 1))
            .isInstanceOf(NotFoundException.class);
    }

    // ─── listForMe : admin vs staff vs outsider ──────────────────────────────────

    @Test
    void listForMe_tenantAdmin_returnsAllForAdmin() {
        when(repo.isTenantAdmin(caller, tenant)).thenReturn(true);
        when(repo.findAllForAdmin(tenant)).thenReturn(List.of(
            announcement(true, Instant.now()), announcement(false, Instant.now().plus(1, ChronoUnit.DAYS))));

        List<AnnouncementDto> out = service.listForMe();

        assertThat(out).hasSize(2);
        verify(repo).findAllForAdmin(tenant);
        verify(repo, never()).findActiveForTenant(any(), any());
    }

    @Test
    void listForMe_staff_returnsActiveForTenant() {
        when(repo.isTenantAdmin(caller, tenant)).thenReturn(false);
        when(repo.isActiveStaffOfTenant(caller, tenant)).thenReturn(true);
        when(repo.findActiveForTenant(eq(tenant), any())).thenReturn(List.of(
            announcement(true, Instant.now().minus(1, ChronoUnit.HOURS))));

        List<AnnouncementDto> out = service.listForMe();

        assertThat(out).hasSize(1);
        verify(repo).findActiveForTenant(eq(tenant), any());
        verify(repo, never()).findAllForAdmin(any());
    }

    @Test
    void listForMe_neitherAdminNorStaff_returnsEmpty() {
        when(repo.isTenantAdmin(caller, tenant)).thenReturn(false);
        when(repo.isActiveStaffOfTenant(caller, tenant)).thenReturn(false);
        assertThat(service.listForMe()).isEmpty();
    }

    @Test
    void listForMe_noTenant_returnsEmpty() {
        when(userDirectory.tenantIdById(caller)).thenReturn(Optional.empty());
        assertThat(service.listForMe()).isEmpty();
    }

    @Test
    void listForMe_marksReadByMe_whenAckVersionMatchesCurrent() {
        when(repo.isTenantAdmin(caller, tenant)).thenReturn(false);
        when(repo.isActiveStaffOfTenant(caller, tenant)).thenReturn(true);
        Announcement a = announcement(true, Instant.now().minus(1, ChronoUnit.HOURS)); // bodyVersion=1
        when(repo.findActiveForTenant(eq(tenant), any())).thenReturn(List.of(a));
        // Le caller a acquitté la version 1 → readByMe true.
        when(readRepo.findReadAnnouncementIdsForUser(eq(caller), any()))
            .thenReturn(List.<Object[]>of(new Object[]{a.getId(), 1}));

        List<AnnouncementDto> out = service.listForMe();

        assertThat(out).hasSize(1);
        assertThat(out.get(0).readByMe()).isTrue();
    }

    // ─── archive / softDelete : admin-only ───────────────────────────────────────

    @Test
    void archive_byNonAdmin_forbidden() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(announcement(true, Instant.now())));
        when(repo.isTenantAdmin(caller, tenant)).thenReturn(false);
        assertThatThrownBy(() -> service.archive(id)).isInstanceOf(ForbiddenException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void archive_byAdmin_setsArchivedAt_andUnpins() {
        securityMock.when(SecurityHelper::isAdmin).thenReturn(true);
        UUID id = UUID.randomUUID();
        Announcement a = announcement(true, Instant.now());
        when(repo.findById(id)).thenReturn(Optional.of(a));

        AnnouncementDto out = service.archive(id);

        assertThat(out.archivedAt()).isNotNull();
        assertThat(out.pinned()).isFalse();
        verify(repo).save(a);
    }

    @Test
    void softDelete_byAdmin_setsDeletedAt() {
        securityMock.when(SecurityHelper::isAdmin).thenReturn(true);
        UUID id = UUID.randomUUID();
        Announcement a = announcement(true, Instant.now());
        when(repo.findById(id)).thenReturn(Optional.of(a));

        service.softDelete(id);

        assertThat(a.getDeletedAt()).isNotNull();
        verify(repo).save(a);
    }

    @Test
    void softDelete_alreadyDeleted_isNoOp() {
        UUID id = UUID.randomUUID();
        Announcement a = announcement(true, Instant.now());
        a.softDelete(Instant.now());
        when(repo.findById(id)).thenReturn(Optional.of(a));
        // Idempotent : pas d'exception, pas de save, et l'ABAC admin n'est même pas évalué.
        service.softDelete(id);
        verify(repo, never()).save(any());
    }

    @Test
    void softDelete_byNonAdmin_forbidden() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(announcement(true, Instant.now())));
        when(repo.isTenantAdmin(caller, tenant)).thenReturn(false);
        assertThatThrownBy(() -> service.softDelete(id)).isInstanceOf(ForbiddenException.class);
        verify(repo, never()).save(any());
    }
}
