package com.onesley.oneclick.modules.stories.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.stories.api.PccStoryDtos.CreateStoryDto;
import com.onesley.oneclick.modules.stories.api.PccStoryDtos.StoryDto;
import com.onesley.oneclick.modules.stories.api.PccStoryDtos.StoryViewCountDto;
import com.onesley.oneclick.modules.stories.api.PccStoryDtos.UpdateStoryDto;
import com.onesley.oneclick.security.SecurityHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

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
 * Tests unitaires Mockito de {@link PccStoryService} (modules.stories).
 *
 * <p>Couvre l'ABAC + les défauts/validations portés par le service :
 * <ul>
 *   <li><b>listForMe</b> : staff/admin du tenant → {@code findAllForStaff} (toutes) ; membre (CLIENT)
 *       → {@code findActiveForTenant} (vivantes) ; sans tenant → vide.</li>
 *   <li><b>create</b> : staff du tenant requis (403 sinon) ; sans tenant → 400 ; mediaType invalide
 *       → 400 ; expiresAt avant publishAt → 400 ; défauts appliqués (image/15/0/now).</li>
 *   <li><b>update</b> : staff/admin du tenant requis (403 sinon) ; 404 si introuvable.</li>
 *   <li><b>softDelete</b> : admin-only/staff-only (403 sinon) ; idempotent si déjà supprimée.</li>
 * </ul>
 *
 * <p>{@link SecurityHelper} (statique) simulé pour toute la classe (même pattern que
 * {@code AnnouncementServiceTest}). Caller = {@link #caller}.</p>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class PccStoryServiceTest {

    @Mock PccStoryRepository repo;
    @Mock PccStoryViewRepository viewRepo;
    @Mock UserDirectoryApi userDirectory;
    @Mock MembershipDirectoryApi membershipDirectory;
    @InjectMocks PccStoryService service;

    private MockedStatic<SecurityHelper> securityMock;

    private final UUID caller = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setup() {
        securityMock = mockStatic(SecurityHelper.class);
        securityMock.when(SecurityHelper::currentUserId).thenReturn(caller);
        securityMock.when(SecurityHelper::isAdmin).thenReturn(false);
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(false);
        lenient().when(userDirectory.tenantIdById(caller)).thenReturn(Optional.of(tenant));
        lenient().when(userDirectory.nameById(any())).thenReturn(Optional.empty());
        // P3 : callerProgramTenant = activeTenantIds(caller).findFirst().orElse(tenantIdById). On laisse
        // activeTenantIds VIDE → fallback sur tenantIdById (= comportement historique des tests).
        lenient().when(membershipDirectory.activeTenantIds(caller)).thenReturn(List.of());
        // markViewed (P3) : appartenance par membership → le caller est membre actif de `tenant`.
        lenient().when(membershipDirectory.isActiveMember(caller, tenant)).thenReturn(true);
        lenient().when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        // Gap #7 — défaut : aucune story vue (les tests d'enrichissement la surchargent).
        lenient().when(viewRepo.findViewedStoryIds(any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        if (securityMock != null) securityMock.close();
    }

    private PccStory story(Instant publishAt, Instant expiresAt) {
        return new PccStory(UUID.randomUUID(), tenant, caller,
            "https://cdn/x.jpg", "image", "Légende", 15, 0, publishAt, expiresAt);
    }

    /** Simule un caller staff actif du tenant (acteur gestion + appartenance tenant). */
    private void asTenantStaff() {
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
        when(repo.isActiveStaffOfTenant(caller, tenant)).thenReturn(true);
    }

    // ─── create : write staff-only + défauts + validations ───────────────────────

    @Test
    void create_byNonStaff_forbidden() {
        // CLIENT (isStaffOrAdmin=false) → 403, sans même toucher la read-view staff.
        assertThatThrownBy(() -> service.create(new CreateStoryDto(
            "https://cdn/x.jpg", "image", "cap", 15, 0, null, null)))
            .isInstanceOf(ForbiddenException.class);
        verify(repo, never()).save(any());
    }

    // ─── adminCreate (Lot 4b — cross-tenant super-admin) ─────────────────────────

    @Test
    void adminCreate_usesTargetTenant_notCallerTenant() {
        UUID targetTenant = UUID.randomUUID(); // tenant CIBLE ≠ tenant home du caller
        StoryDto out = service.adminCreate(targetTenant, new CreateStoryDto(
            "https://cdn/admin.jpg", "image", "Depuis le super-admin", 15, 0, null, null));
        assertThat(out.tenantId()).isEqualTo(targetTenant);
        assertThat(out.authorId()).isEqualTo(caller);
        // Aucune garde staff ici (gardé au controller par UPDATE:TENANTS) → insert direct.
        verify(repo, never()).isActiveStaffOfTenant(any(), any());
        verify(repo).save(any(PccStory.class));
    }

    @Test
    void adminCreate_nullTenant_throws400() {
        assertThatThrownBy(() -> service.adminCreate(null, new CreateStoryDto(
            "https://cdn/x.jpg", "image", "cap", 15, 0, null, null)))
            .isInstanceOf(BadRequestException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_byRestaurateurOfOtherTenant_forbidden() {
        // Acteur gestion MAIS pas staff actif de CE tenant (cross-tenant) → 403.
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
        when(repo.isActiveStaffOfTenant(caller, tenant)).thenReturn(false);
        assertThatThrownBy(() -> service.create(new CreateStoryDto(
            "https://cdn/x.jpg", "image", "cap", 15, 0, null, null)))
            .isInstanceOf(ForbiddenException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_noTenant_throws400() {
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
        when(userDirectory.tenantIdById(caller)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(new CreateStoryDto(
            "https://cdn/x.jpg", "image", "cap", 15, 0, null, null)))
            .isInstanceOf(BadRequestException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_byTenantStaff_appliesDefaults_andSaves() {
        asTenantStaff();
        // mediaType/durationS/sortOrder/publishAt null → défauts image/15/0/now.
        StoryDto out = service.create(new CreateStoryDto(
            "https://cdn/x.jpg", null, "  Brunch dominical  ", null, null, null, null));

        assertThat(out.tenantId()).isEqualTo(tenant);
        assertThat(out.authorId()).isEqualTo(caller);
        assertThat(out.mediaType()).isEqualTo("image");
        assertThat(out.durationS()).isEqualTo(15);
        assertThat(out.sortOrder()).isEqualTo(0);
        assertThat(out.caption()).isEqualTo("Brunch dominical"); // trimmé
        assertThat(out.publishAt()).isNotNull();
        assertThat(out.visible()).isTrue(); // publishAt now, pas d'expiration
        verify(repo).save(any(PccStory.class));
    }

    @Test
    void create_videoType_kept() {
        asTenantStaff();
        StoryDto out = service.create(new CreateStoryDto(
            "https://cdn/clip.mp4", "video", null, 30, 2, null, null));
        assertThat(out.mediaType()).isEqualTo("video");
        assertThat(out.durationS()).isEqualTo(30);
        assertThat(out.sortOrder()).isEqualTo(2);
    }

    @Test
    void create_invalidMediaType_throws400() {
        asTenantStaff();
        assertThatThrownBy(() -> service.create(new CreateStoryDto(
            "https://cdn/x.jpg", "gif", null, 15, 0, null, null)))
            .isInstanceOf(BadRequestException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_blankMediaUrl_throws400() {
        asTenantStaff();
        assertThatThrownBy(() -> service.create(new CreateStoryDto(
            "   ", "image", null, 15, 0, null, null)))
            .isInstanceOf(BadRequestException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_expiresBeforePublish_throws400() {
        asTenantStaff();
        Instant publish = Instant.now();
        Instant expires = publish.minus(1, ChronoUnit.HOURS); // expiration avant publication → invalide
        assertThatThrownBy(() -> service.create(new CreateStoryDto(
            "https://cdn/x.jpg", "image", null, 15, 0, publish, expires)))
            .isInstanceOf(BadRequestException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_scheduledFuture_notVisibleYet() {
        asTenantStaff();
        Instant future = Instant.now().plus(2, ChronoUnit.DAYS);
        StoryDto out = service.create(new CreateStoryDto(
            "https://cdn/x.jpg", "image", null, 15, 0, future, null));
        assertThat(out.visible()).isFalse(); // programmée → pas encore visible des membres
    }

    // ─── update : staff-only + 404 ───────────────────────────────────────────────

    @Test
    void update_byAdmin_replacesFields() {
        securityMock.when(SecurityHelper::isAdmin).thenReturn(true); // admin global → bypass staff tenant
        UUID id = UUID.randomUUID();
        PccStory existing = story(Instant.now().minus(1, ChronoUnit.HOURS), null);
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        StoryDto out = service.update(id, new UpdateStoryDto(
            "https://cdn/new.mp4", "video", "Nouvelle légende", 20, 5, null, null));

        assertThat(out.mediaUrl()).isEqualTo("https://cdn/new.mp4");
        assertThat(out.mediaType()).isEqualTo("video");
        assertThat(out.caption()).isEqualTo("Nouvelle légende");
        assertThat(out.durationS()).isEqualTo(20);
        assertThat(out.sortOrder()).isEqualTo(5);
        verify(repo).save(existing);
    }

    @Test
    void update_byNonStaff_forbidden() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(story(Instant.now(), null)));
        // CLIENT → isStaffOrAdmin false → 403.
        assertThatThrownBy(() -> service.update(id, new UpdateStoryDto(
            "https://cdn/x.jpg", "image", null, 15, 0, null, null)))
            .isInstanceOf(ForbiddenException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void update_notFound_throws404() {
        securityMock.when(SecurityHelper::isAdmin).thenReturn(true);
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(id, new UpdateStoryDto(
            "https://cdn/x.jpg", "image", null, 15, 0, null, null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_softDeleted_throws404() {
        securityMock.when(SecurityHelper::isAdmin).thenReturn(true);
        UUID id = UUID.randomUUID();
        PccStory deleted = story(Instant.now(), null);
        deleted.softDelete(Instant.now());
        when(repo.findById(id)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.update(id, new UpdateStoryDto(
            "https://cdn/x.jpg", "image", null, 15, 0, null, null)))
            .isInstanceOf(NotFoundException.class);
    }

    // ─── softDelete : staff-only + idempotent ────────────────────────────────────

    @Test
    void softDelete_byStaff_setsDeletedAt() {
        asTenantStaff();
        UUID id = UUID.randomUUID();
        PccStory s = story(Instant.now(), null);
        when(repo.findById(id)).thenReturn(Optional.of(s));

        service.softDelete(id);

        assertThat(s.getDeletedAt()).isNotNull();
        verify(repo).save(s);
    }

    @Test
    void softDelete_byNonStaff_forbidden() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(story(Instant.now(), null)));
        assertThatThrownBy(() -> service.softDelete(id)).isInstanceOf(ForbiddenException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void softDelete_alreadyDeleted_isNoOp() {
        UUID id = UUID.randomUUID();
        PccStory s = story(Instant.now(), null);
        s.softDelete(Instant.now());
        when(repo.findById(id)).thenReturn(Optional.of(s));
        // Idempotent : pas d'exception, pas de save, l'ABAC n'est même pas évalué.
        service.softDelete(id);
        verify(repo, never()).save(any());
    }

    // ─── listForMe : staff vs membre vs sans-tenant ──────────────────────────────

    @Test
    void listForMe_tenantStaff_returnsAllForStaff() {
        asTenantStaff();
        when(repo.findAllForStaff(tenant)).thenReturn(List.of(
            story(Instant.now(), null), story(Instant.now().plus(1, ChronoUnit.DAYS), null)));

        List<StoryDto> out = service.listForMe();

        assertThat(out).hasSize(2);
        verify(repo).findAllForStaff(tenant);
        verify(repo, never()).findActiveForTenant(any(), any());
    }

    @Test
    void listForMe_member_returnsActiveForTenant() {
        // CLIENT (isStaffOrAdmin false) → vue membre vivantes uniquement.
        when(repo.findActiveForTenant(eq(tenant), any())).thenReturn(List.of(
            story(Instant.now().minus(1, ChronoUnit.HOURS), null)));

        List<StoryDto> out = service.listForMe();

        assertThat(out).hasSize(1);
        verify(repo).findActiveForTenant(eq(tenant), any());
        verify(repo, never()).findAllForStaff(any());
    }

    @Test
    void listForMe_noTenant_returnsEmpty() {
        when(userDirectory.tenantIdById(caller)).thenReturn(Optional.empty());
        assertThat(service.listForMe()).isEmpty();
        verify(repo, never()).findActiveForTenant(any(), any());
        verify(repo, never()).findAllForStaff(any());
    }

    @Test
    void listForMe_restaurateurOfOtherTenant_fallsBackToMemberView() {
        // Acteur gestion mais PAS staff actif de ce tenant → ne gère pas → vue membre (vivantes).
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
        when(repo.isActiveStaffOfTenant(caller, tenant)).thenReturn(false);
        when(repo.findActiveForTenant(eq(tenant), any())).thenReturn(List.of());

        service.listForMe();

        verify(repo).findActiveForTenant(eq(tenant), any());
        verify(repo, never()).findAllForStaff(any());
    }

    // ─── Gap #7 — markViewed / viewCounts / enrichissement `viewed` ───────────────

    @Test
    void markViewed_member_recordsWhenNew() {
        PccStory s = story(Instant.now(), null);
        when(repo.findById(s.getId())).thenReturn(Optional.of(s));
        when(viewRepo.existsByStoryIdAndUserId(s.getId(), caller)).thenReturn(false);

        service.markViewed(s.getId());

        verify(viewRepo).save(any(PccStoryView.class));
    }

    @Test
    void markViewed_idempotent_skipsWhenAlreadyViewed() {
        PccStory s = story(Instant.now(), null);
        when(repo.findById(s.getId())).thenReturn(Optional.of(s));
        when(viewRepo.existsByStoryIdAndUserId(s.getId(), caller)).thenReturn(true);

        service.markViewed(s.getId());

        verify(viewRepo, never()).save(any());
    }

    @Test
    void markViewed_crossTenant_forbidden() {
        // Story d'un AUTRE tenant que celui du caller (non-admin) → 403.
        UUID otherTenant = UUID.randomUUID();
        PccStory s = new PccStory(UUID.randomUUID(), otherTenant, caller,
            "https://cdn/x.jpg", "image", null, 15, 0, Instant.now(), null);
        when(repo.findById(s.getId())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.markViewed(s.getId())).isInstanceOf(ForbiddenException.class);
        verify(viewRepo, never()).save(any());
    }

    @Test
    void markViewed_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markViewed(id)).isInstanceOf(NotFoundException.class);
        verify(viewRepo, never()).save(any());
    }

    @Test
    void viewCounts_member_forbidden() {
        // CLIENT (pas staff du tenant) → pas de stats → 403.
        assertThatThrownBy(() -> service.viewCounts()).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void viewCounts_staff_returnsAggregates() {
        asTenantStaff();
        PccStory a = story(Instant.now(), null);
        PccStory b = story(Instant.now(), null);
        when(repo.findAllForStaff(tenant)).thenReturn(List.of(a, b));
        when(viewRepo.countByStoryIds(List.of(a.getId(), b.getId())))
            .thenReturn(List.<Object[]>of(new Object[]{a.getId(), 3L}, new Object[]{b.getId(), 1L}));

        List<StoryViewCountDto> out = service.viewCounts();

        assertThat(out).hasSize(2);
        assertThat(out).anyMatch(c -> c.storyId().equals(a.getId()) && c.viewCount() == 3L);
        assertThat(out).anyMatch(c -> c.storyId().equals(b.getId()) && c.viewCount() == 1L);
    }

    @Test
    void listForMe_member_enrichesViewedFlag() {
        PccStory seen = story(Instant.now().minus(1, ChronoUnit.HOURS), null);
        PccStory unseen = story(Instant.now().minus(2, ChronoUnit.HOURS), null);
        when(repo.findActiveForTenant(eq(tenant), any())).thenReturn(List.of(seen, unseen));
        when(viewRepo.findViewedStoryIds(eq(caller), any())).thenReturn(List.of(seen.getId()));

        List<StoryDto> out = service.listForMe();

        assertThat(out).hasSize(2);
        assertThat(out.stream().filter(d -> d.id().equals(seen.getId())).findFirst().orElseThrow().viewed()).isTrue();
        assertThat(out.stream().filter(d -> d.id().equals(unseen.getId())).findFirst().orElseThrow().viewed()).isFalse();
    }
}
