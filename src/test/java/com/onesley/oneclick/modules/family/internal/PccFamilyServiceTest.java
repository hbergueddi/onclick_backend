package com.onesley.oneclick.modules.family.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi.UserName;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.exception.UnprocessableException;
import com.onesley.oneclick.modules.family.api.PccFamilyDtos.AddFamilyMemberDto;
import com.onesley.oneclick.modules.family.api.PccFamilyDtos.FamilyMemberDto;
import com.onesley.oneclick.shared.events.FamilyMemberAddedEvent;
import com.onesley.oneclick.security.SecurityHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

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
 * Tests unitaires Mockito de {@link PccFamilyService} (L3 — modules.family).
 *
 * <p>Couvre les invariants du port legacy : limite 10 → 422, idempotence (déjà ajouté),
 * self-check (ajout de soi → 400), relation-check (historique sans relation → 403),
 * target-not-found → 404, et l'autorisation du retrait (A ou B uniquement).</p>
 *
 * <p>{@link SecurityHelper#currentUserId()} (statique) est simulé pour TOUTE la classe :
 * le caller (A) = {@link #caller} (même pattern MockedStatic que {@code EventServiceTest}).</p>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class PccFamilyServiceTest {

    @Mock PccFamilyMemberRepository repo;
    @Mock UserDirectoryApi userDirectory;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks PccFamilyService service;

    private MockedStatic<SecurityHelper> securityMock;

    private final UUID caller = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();

    @BeforeEach
    void setup() {
        securityMock = mockStatic(SecurityHelper.class);
        securityMock.when(SecurityHelper::currentUserId).thenReturn(caller);
        // tenant du caller résolu par défaut (sauf override).
        lenient().when(userDirectory.tenantIdById(caller)).thenReturn(Optional.of(tenant));
        lenient().when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        if (securityMock != null) securityMock.close();
    }

    private UserName targetName() {
        return new UserName(target, "Sara", "Bennani", "+212600000000", "sara@x.ma", "http://avatar");
    }

    // ─── add ────────────────────────────────────────────────────────────────────

    @Test
    void add_targetNotFound_throws404() {
        when(userDirectory.findByIdentifier("ghost@x.ma", tenant)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addFamilyMember(new AddFamilyMemberDto("ghost@x.ma", "Enfant")))
            .isInstanceOf(NotFoundException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void add_self_throws400() {
        // le proche résolu = le caller → CANNOT_ADD_SELF.
        when(userDirectory.findByIdentifier("me@x.ma", tenant))
            .thenReturn(Optional.of(new UserName(caller, "Moi", "Même", null, "me@x.ma", null)));
        assertThatThrownBy(() -> service.addFamilyMember(new AddFamilyMemberDto("me@x.ma", null)))
            .isInstanceOf(BadRequestException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void add_idempotent_returnsExisting_noSave_noEvent() {
        when(userDirectory.findByIdentifier("sara@x.ma", tenant)).thenReturn(Optional.of(targetName()));
        PccFamilyMember existing = new PccFamilyMember(UUID.randomUUID(), caller, target, "Conjoint·e");
        when(repo.findByMemberIdAndRelatedMemberId(caller, target)).thenReturn(Optional.of(existing));

        FamilyMemberDto dto = service.addFamilyMember(new AddFamilyMemberDto("sara@x.ma", "Conjoint·e"));

        assertThat(dto.status()).isEqualTo("already_added");
        assertThat(dto.memberId()).isEqualTo(target);
        verify(repo, never()).save(any());
        verify(repo, never()).countByMemberId(any()); // pas de check limite sur idempotent
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void add_maxReached_throws422() {
        when(userDirectory.findByIdentifier("sara@x.ma", tenant)).thenReturn(Optional.of(targetName()));
        when(repo.findByMemberIdAndRelatedMemberId(caller, target)).thenReturn(Optional.empty());
        when(repo.countByMemberId(caller)).thenReturn(10L); // limite atteinte

        assertThatThrownBy(() -> service.addFamilyMember(new AddFamilyMemberDto("sara@x.ma", "Autre")))
            .isInstanceOf(UnprocessableException.class)
            .hasMessageContaining("Limite de 10 membres");
        verify(repo, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void add_success_savesAndPublishesEvent() {
        when(userDirectory.findByIdentifier("sara@x.ma", tenant)).thenReturn(Optional.of(targetName()));
        when(repo.findByMemberIdAndRelatedMemberId(caller, target)).thenReturn(Optional.empty());
        when(repo.countByMemberId(caller)).thenReturn(3L);

        FamilyMemberDto dto = service.addFamilyMember(new AddFamilyMemberDto("sara@x.ma", "  Conjoint·e  "));

        assertThat(dto.status()).isEqualTo("added");
        assertThat(dto.memberId()).isEqualTo(target);
        assertThat(dto.firstName()).isEqualTo("Sara");
        assertThat(dto.avatarUrl()).isEqualTo("http://avatar");

        ArgumentCaptor<PccFamilyMember> cap = ArgumentCaptor.forClass(PccFamilyMember.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getMemberId()).isEqualTo(caller);
        assertThat(cap.getValue().getRelatedMemberId()).isEqualTo(target);
        assertThat(cap.getValue().getRelation()).isEqualTo("Conjoint·e"); // trimmé

        ArgumentCaptor<FamilyMemberAddedEvent> ev = ArgumentCaptor.forClass(FamilyMemberAddedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().memberId()).isEqualTo(caller);
        assertThat(ev.getValue().relatedMemberId()).isEqualTo(target);
    }

    @Test
    void add_noTenant_throws400() {
        when(userDirectory.tenantIdById(caller)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addFamilyMember(new AddFamilyMemberDto("sara@x.ma", null)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void add_notAuthenticated_throws403() {
        securityMock.when(SecurityHelper::currentUserId).thenReturn(null);
        assertThatThrownBy(() -> service.addFamilyMember(new AddFamilyMemberDto("sara@x.ma", null)))
            .isInstanceOf(ForbiddenException.class);
    }

    // ─── list ─────────────────────────────────────────────────────────────────────

    @Test
    void list_mapsNameAndTotalPoints() {
        PccFamilyMember link = new PccFamilyMember(UUID.randomUUID(), caller, target, "Enfant");
        when(repo.findByMemberIdOrderByCreatedAtDesc(caller)).thenReturn(List.of(link));
        when(userDirectory.nameById(target)).thenReturn(Optional.of(targetName()));
        when(repo.sumRemainingPointsByClientInTenant(target, tenant)).thenReturn(675L);

        List<FamilyMemberDto> res = service.listMyFamily();

        assertThat(res).hasSize(1);
        assertThat(res.get(0).memberId()).isEqualTo(target);
        assertThat(res.get(0).firstName()).isEqualTo("Sara");
        assertThat(res.get(0).totalRemainingPoints()).isEqualTo(675L);
        assertThat(res.get(0).relation()).isEqualTo("Enfant");
        assertThat(res.get(0).status()).isNull();
    }

    @Test
    void list_noTenant_returnsEmpty() {
        when(repo.findByMemberIdOrderByCreatedAtDesc(caller))
            .thenReturn(List.of(new PccFamilyMember(UUID.randomUUID(), caller, target, "Enfant")));
        when(userDirectory.tenantIdById(caller)).thenReturn(Optional.empty());
        assertThat(service.listMyFamily()).isEmpty();
    }

    // ─── points-history (relation-check) ────────────────────────────────────────

    @Test
    void history_noRelation_throws403() {
        when(repo.findByMemberIdAndRelatedMemberId(caller, target)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.memberPointsHistory(target))
            .isInstanceOf(ForbiddenException.class);
        verify(repo, never()).pointsHistoryByClientInTenant(any(), any());
    }

    @Test
    void history_withRelation_readsView() {
        when(repo.findByMemberIdAndRelatedMemberId(caller, target))
            .thenReturn(Optional.of(new PccFamilyMember(UUID.randomUUID(), caller, target, "Parent")));
        when(repo.pointsHistoryByClientInTenant(target, tenant)).thenReturn(List.of());
        assertThat(service.memberPointsHistory(target)).isEmpty();
        verify(repo).pointsHistoryByClientInTenant(target, tenant);
    }

    // ─── remove (A ou B) ────────────────────────────────────────────────────────

    @Test
    void remove_notFound_throws404() {
        UUID relId = UUID.randomUUID();
        when(repo.findById(relId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.removeFamilyMember(relId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void remove_byOwnerA_succeeds() {
        UUID relId = UUID.randomUUID();
        PccFamilyMember link = new PccFamilyMember(relId, caller, target, "Enfant"); // caller = A
        when(repo.findById(relId)).thenReturn(Optional.of(link));
        service.removeFamilyMember(relId);
        verify(repo).delete(link);
    }

    @Test
    void remove_byRelatedB_succeeds() {
        UUID relId = UUID.randomUUID();
        PccFamilyMember link = new PccFamilyMember(relId, target, caller, "Enfant"); // caller = B
        when(repo.findById(relId)).thenReturn(Optional.of(link));
        service.removeFamilyMember(relId);
        verify(repo).delete(link);
    }

    @Test
    void remove_byStranger_throws403() {
        UUID relId = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        PccFamilyMember link = new PccFamilyMember(relId, other, target, "Enfant"); // caller ni A ni B
        when(repo.findById(relId)).thenReturn(Optional.of(link));
        assertThatThrownBy(() -> service.removeFamilyMember(relId)).isInstanceOf(ForbiddenException.class);
        verify(repo, never()).delete(any());
    }
}
