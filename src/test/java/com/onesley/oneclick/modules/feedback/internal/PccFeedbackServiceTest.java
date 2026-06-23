package com.onesley.oneclick.modules.feedback.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.feedback.api.PccFeedbackDtos.CreateFeedbackDto;
import com.onesley.oneclick.modules.feedback.api.PccFeedbackDtos.FeedbackDto;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.FeedbackCreatedEvent;
import com.onesley.oneclick.shared.events.FeedbackRepliedEvent;
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
 * Tests unitaires Mockito de {@link PccFeedbackService} (L3 — modules.feedback, PCC Lot 7).
 *
 * <p>Couvre le port legacy + l'ABAC : create (member/tenant du caller, sentiment invalide → 400,
 * pas de tenant → 400, event + STOMP publiés), reply (non-staff → 403, owner non-propriétaire → 403,
 * déjà répondu → 409, admin OK + event/STOMP), mark-read (membre OK, tiers → 403), listMine.</p>
 *
 * <p>{@link SecurityHelper} (statique) simulé pour toute la classe (même pattern que
 * {@code PccFamilyServiceTest}/{@code EventServiceTest}). Caller = {@link #caller}.</p>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class PccFeedbackServiceTest {

    @Mock PccFeedbackRepository repo;
    @Mock UserDirectoryApi userDirectory;
    @Mock MembershipDirectoryApi membershipDirectory;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock FeedbackPublisher feedbackPublisher;
    @InjectMocks PccFeedbackService service;

    private MockedStatic<SecurityHelper> securityMock;

    private final UUID caller = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();
    private final UUID restaurant = UUID.randomUUID();

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
        lenient().when(repo.findFeedbackRecipientIds(any(), any(), any())).thenReturn(List.of(UUID.randomUUID()));
        // Par défaut, le resto ciblé appartient au tenant du caller (garde-fou anti-spoof passant).
        lenient().when(repo.restaurantBelongsToTenant(any(), any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (securityMock != null) securityMock.close();
    }

    private PccFeedback feedback(UUID memberId, UUID target) {
        return new PccFeedback(UUID.randomUUID(), memberId, tenant, "unhappy", "Padel", "RAS", target);
    }

    // ─── create ────────────────────────────────────────────────────────────────

    @Test
    void create_persists_andPublishesEventAndStomp() {
        FeedbackDto out = service.create(new CreateFeedbackDto("happy", "Service exemplaire", "Bravo", restaurant));

        assertThat(out.memberId()).isEqualTo(caller);
        assertThat(out.sentiment()).isEqualTo("happy");
        verify(repo).save(any(PccFeedback.class));
        verify(eventPublisher).publishEvent(any(FeedbackCreatedEvent.class));
        verify(feedbackPublisher).publishCreated(any(FeedbackDto.class));
    }

    @Test
    void create_invalidSentiment_throws400() {
        assertThatThrownBy(() -> service.create(new CreateFeedbackDto("meh", "Padel", null, null)))
            .isInstanceOf(BadRequestException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void create_blankCategory_throws400() {
        assertThatThrownBy(() -> service.create(new CreateFeedbackDto("happy", " ", null, null)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_noTenant_throws400() {
        when(userDirectory.tenantIdById(caller)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(new CreateFeedbackDto("happy", "Padel", null, null)))
            .isInstanceOf(BadRequestException.class);
        verify(repo, never()).save(any());
    }

    /**
     * FUITE DE PÉRIMÈTRE (fix 17/06) : un avis CIBLÉ dont le resto n'appartient PAS au tenant du
     * membre est rejeté (403) AVANT persistance — sinon l'owner d'un autre tenant recevrait la notif
     * « nouvel avis » (in-app + push). Anti-spoof à la création.
     */
    @Test
    void create_targetRestaurantOtherTenant_forbidden() {
        when(repo.restaurantBelongsToTenant(restaurant, tenant)).thenReturn(false);
        assertThatThrownBy(() -> service.create(new CreateFeedbackDto("unhappy", "Padel", "Souci", restaurant)))
            .isInstanceOf(ForbiddenException.class);
        verify(repo, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    /** Avis GÉNÉRAL (target null) : pas de validation resto/tenant, persistance OK. */
    @Test
    void create_generalFeedback_noTargetValidation() {
        FeedbackDto out = service.create(new CreateFeedbackDto("happy", "Accueil", "Top", null));
        assertThat(out.targetRestaurantId()).isNull();
        verify(repo, never()).restaurantBelongsToTenant(any(), any());
        verify(repo).save(any(PccFeedback.class));
    }

    // ─── reply ─────────────────────────────────────────────────────────────────

    @Test
    void reply_byNonStaff_forbidden() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.reply(id, "Merci de votre retour"))
            .isInstanceOf(ForbiddenException.class);
        verify(repo, never()).findById(any());
    }

    @Test
    void reply_admin_appliesReply_andPublishes() {
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
        securityMock.when(SecurityHelper::isAdmin).thenReturn(true);
        UUID id = UUID.randomUUID();
        PccFeedback fb = feedback(UUID.randomUUID(), restaurant);
        when(repo.findById(id)).thenReturn(Optional.of(fb));

        FeedbackDto out = service.reply(id, "Merci, on s'en occupe.");

        assertThat(out.replyText()).isEqualTo("Merci, on s'en occupe.");
        verify(repo).save(fb);
        verify(eventPublisher).publishEvent(any(FeedbackRepliedEvent.class));
        verify(feedbackPublisher).publishReply(any(), any(FeedbackDto.class));
    }

    @Test
    void reply_ownerNotOwningTarget_forbidden() {
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
        securityMock.when(SecurityHelper::isAdmin).thenReturn(false); // owner « pur »
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(feedback(UUID.randomUUID(), restaurant)));
        when(repo.isActiveOwnerOf(caller, restaurant)).thenReturn(false);

        assertThatThrownBy(() -> service.reply(id, "Réponse"))
            .isInstanceOf(ForbiddenException.class);
        verify(repo, never()).save(any());
    }

    /**
     * AMPLIFICATION DE FUITE (fix 17/06) : un owner non-admin ne peut pas répondre à un avis d'un
     * AUTRE tenant que le sien — neutralise le vecteur cross-tenant (un avis qui aurait fuité vers
     * son resto). 403 avant même le check owner-scope.
     */
    @Test
    void reply_ownerCrossTenant_forbidden() {
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
        securityMock.when(SecurityHelper::isAdmin).thenReturn(false); // owner « pur »
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(feedback(UUID.randomUUID(), restaurant))); // fb.tenant = tenant
        when(userDirectory.tenantIdById(caller)).thenReturn(Optional.of(UUID.randomUUID())); // caller d'un AUTRE tenant

        assertThatThrownBy(() -> service.reply(id, "Réponse cross-tenant"))
            .isInstanceOf(ForbiddenException.class);
        verify(repo, never()).isActiveOwnerOf(any(), any());
        verify(repo, never()).save(any());
    }

    @Test
    void reply_alreadyReplied_conflict() {
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
        securityMock.when(SecurityHelper::isAdmin).thenReturn(true);
        UUID id = UUID.randomUUID();
        PccFeedback fb = feedback(UUID.randomUUID(), null);
        fb.applyReply("Déjà répondu", UUID.randomUUID(), Instant.now());
        when(repo.findById(id)).thenReturn(Optional.of(fb));

        assertThatThrownBy(() -> service.reply(id, "Seconde réponse"))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void reply_notFound_throws404() {
        securityMock.when(SecurityHelper::isStaffOrAdmin).thenReturn(true);
        securityMock.when(SecurityHelper::isAdmin).thenReturn(true);
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reply(id, "Réponse"))
            .isInstanceOf(NotFoundException.class);
    }

    // ─── markReplyRead ───────────────────────────────────────────────────────────

    @Test
    void markReplyRead_byMember_ok() {
        UUID id = UUID.randomUUID();
        PccFeedback fb = feedback(caller, null); // caller EST le membre
        fb.applyReply("Réponse Adil", UUID.randomUUID(), Instant.now());
        when(repo.findById(id)).thenReturn(Optional.of(fb));

        FeedbackDto out = service.markReplyRead(id);

        assertThat(out.replyReadByMember()).isTrue();
        verify(repo).save(fb);
    }

    @Test
    void markReplyRead_byOther_forbidden() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(feedback(UUID.randomUUID(), null))); // membre = autre
        assertThatThrownBy(() -> service.markReplyRead(id))
            .isInstanceOf(ForbiddenException.class);
        verify(repo, never()).save(any());
    }

    // ─── listMine ────────────────────────────────────────────────────────────────

    @Test
    void listMine_mapsRepoResults() {
        when(repo.findByMemberIdOrderByCreatedAtDesc(caller))
            .thenReturn(List.of(feedback(caller, null), feedback(caller, restaurant)));
        List<FeedbackDto> out = service.listMine();
        assertThat(out).hasSize(2);
        assertThat(out).allMatch(f -> f.memberId().equals(caller));
    }
}
