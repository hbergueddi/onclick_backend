package com.onesley.oneclick.core.auth.internal;

import com.onesley.oneclick.shared.events.MemberEnrollmentInvitedEvent;
import com.onesley.oneclick.shared.events.MemberEnrollmentRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link AccountActivationService} (Gap #10, core.auth).
 *
 * <p>Couvre : création d'invitation sur event d'enrôlement (token hashé + ré-émission email),
 * idempotence (invite pending déjà présente), résolution {@code findRedeemable}
 * (valide / expirée / inconnue / blank) et {@code redeem} (single-use).</p>
 */
@ExtendWith(MockitoExtension.class)
class AccountActivationServiceTest {

    @Mock AccountActivationInviteRepository repo;
    @Mock ApplicationEventPublisher events;
    @InjectMocks AccountActivationService service;

    private static final Instant NOW = Instant.parse("2026-06-07T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private void useFixedClock() {
        ReflectionTestUtils.setField(service, "clock", clock);
    }

    private MemberEnrollmentRequestedEvent enrollEvent(UUID userId) {
        return new MemberEnrollmentRequestedEvent(
            userId, "membre@x.ma", "Yasmine",
            UUID.randomUUID(), "palmeraie", "Palmeraie Country Club",
            UUID.randomUUID(), NOW);
    }

    // ─── Création (listener) ────────────────────────────────────────────────────

    @Test
    void onMemberEnrollmentRequested_createsInvite_andPublishesEmailEvent() {
        useFixedClock();
        UUID userId = UUID.randomUUID();
        when(repo.existsByUserIdAndStatus(userId, "pending")).thenReturn(false);
        when(repo.save(any(AccountActivationInvite.class))).thenAnswer(i -> i.getArgument(0));

        service.onMemberEnrollmentRequested(enrollEvent(userId));

        ArgumentCaptor<AccountActivationInvite> savedCap = ArgumentCaptor.forClass(AccountActivationInvite.class);
        verify(repo).save(savedCap.capture());
        AccountActivationInvite saved = savedCap.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getEmail()).isEqualTo("membre@x.ma");
        assertThat(saved.getStatus()).isEqualTo("pending");
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(AccountActivationService.TTL));
        assertThat(saved.getTokenHash()).hasSize(64); // SHA-256 hex

        ArgumentCaptor<MemberEnrollmentInvitedEvent> evCap = ArgumentCaptor.forClass(MemberEnrollmentInvitedEvent.class);
        verify(events).publishEvent(evCap.capture());
        MemberEnrollmentInvitedEvent ev = evCap.getValue();
        assertThat(ev.userId()).isEqualTo(userId);
        assertThat(ev.email()).isEqualTo("membre@x.ma");
        assertThat(ev.tenantSlug()).isEqualTo("palmeraie");
        assertThat(ev.rawToken()).isNotBlank();
        // Le token CLAIR de l'email hashe vers le tokenHash stocké (jamais le clair en base).
        assertThat(AccountActivationService.sha256Hex(ev.rawToken())).isEqualTo(saved.getTokenHash());
    }

    @Test
    void onMemberEnrollmentRequested_existingPending_skips() {
        UUID userId = UUID.randomUUID();
        when(repo.existsByUserIdAndStatus(userId, "pending")).thenReturn(true);

        service.onMemberEnrollmentRequested(enrollEvent(userId));

        verify(repo, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    // ─── findRedeemable ──────────────────────────────────────────────────────────

    @Test
    void findRedeemable_validToken_returnsInvite() {
        useFixedClock();
        UUID userId = UUID.randomUUID();
        AccountActivationInvite invite = new AccountActivationInvite(
            UUID.randomUUID(), userId, "m@x.ma",
            AccountActivationService.sha256Hex("raw-token"), null, NOW.plusSeconds(3600));
        when(repo.findByTokenHash(AccountActivationService.sha256Hex("raw-token")))
            .thenReturn(Optional.of(invite));

        Optional<AccountActivationService.RedeemableInvite> r = service.findRedeemable("raw-token");

        assertThat(r).isPresent();
        assertThat(r.get().userId()).isEqualTo(userId);
        assertThat(r.get().email()).isEqualTo("m@x.ma");
    }

    @Test
    void findRedeemable_expired_returnsEmpty() {
        useFixedClock();
        AccountActivationInvite invite = new AccountActivationInvite(
            UUID.randomUUID(), UUID.randomUUID(), "m@x.ma",
            AccountActivationService.sha256Hex("raw"), null, NOW.minusSeconds(1)); // expirée
        when(repo.findByTokenHash(any())).thenReturn(Optional.of(invite));

        assertThat(service.findRedeemable("raw")).isEmpty();
    }

    @Test
    void findRedeemable_unknownToken_returnsEmpty() {
        useFixedClock();
        when(repo.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThat(service.findRedeemable("nope")).isEmpty();
    }

    @Test
    void findRedeemable_blankToken_returnsEmpty_noRepoCall() {
        assertThat(service.findRedeemable("  ")).isEmpty();
        verify(repo, never()).findByTokenHash(any());
    }

    // ─── redeem ──────────────────────────────────────────────────────────────────

    @Test
    void redeem_marksAccepted_singleUse() {
        useFixedClock();
        UUID id = UUID.randomUUID();
        AccountActivationInvite invite = new AccountActivationInvite(
            id, UUID.randomUUID(), "m@x.ma", "hash", null, NOW.plusSeconds(3600));
        when(repo.findById(id)).thenReturn(Optional.of(invite));

        service.redeem(id);

        assertThat(invite.getStatus()).isEqualTo("accepted");
        assertThat(invite.getAcceptedAt()).isEqualTo(NOW);
        verify(repo).save(eq(invite));
    }
}
