package com.onesley.oneclick.core.tenant.internal;

import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.core.tenant.api.TenantAdminInviteApi.RedeemableInvite;
import com.onesley.oneclick.core.tenant.api.TenantAdminInviteDtos.CreateInviteDto;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.TenantAdminInvitedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link TenantAdminInviteService} (E2 — invitations tenant-admin).
 * Token hashé (jamais en clair en base), event publié, redeem single-use, assignAdmin idempotent,
 * révocation interdite si déjà acceptée, expiration.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class TenantAdminInviteServiceTest {

    @Mock TenantAdminInviteRepository repo;
    @Mock TenantRepository tenantRepository;
    @Mock TenantAdminRepository tenantAdminRepository;
    @Mock ApplicationEventPublisher events;
    @InjectMocks TenantAdminInviteService service;

    private final Clock fixed = Clock.fixed(Instant.parse("2026-06-07T10:00:00Z"), ZoneOffset.UTC);

    private void useFixedClock() {
        ReflectionTestUtils.setField(service, "clock", fixed);
    }

    @Test
    void sha256Hex_isStableAndNotPlaintext() {
        String h1 = TenantAdminInviteService.sha256Hex("hello-token");
        String h2 = TenantAdminInviteService.sha256Hex("hello-token");
        assertThat(h1).isEqualTo(h2).hasSize(64).isNotEqualTo("hello-token");
    }

    @Test
    void createInvite_savesHashedToken_publishesEvent_returnsDtoWithoutToken() {
        useFixedClock();
        UUID tenantId = UUID.randomUUID();
        UUID inviterId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(new Tenant(tenantId, "PCC", "palmeraie")));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(inviterId);
            var dto = service.createInvite(tenantId, new CreateInviteDto("admin@pcc.ma", "admin"));

            assertThat(dto.email()).isEqualTo("admin@pcc.ma");
            assertThat(dto.role()).isEqualTo("admin");
            assertThat(dto.status()).isEqualTo("pending");
            assertThat(dto.invitedBy()).isEqualTo(inviterId);
            assertThat(dto.expiresAt()).isEqualTo(fixed.instant().plusSeconds(7 * 24 * 3600));
        }

        ArgumentCaptor<TenantAdminInvite> savedCap = ArgumentCaptor.forClass(TenantAdminInvite.class);
        verify(repo).save(savedCap.capture());
        ArgumentCaptor<TenantAdminInvitedEvent> evCap = ArgumentCaptor.forClass(TenantAdminInvitedEvent.class);
        verify(events).publishEvent(evCap.capture());

        // Le token clair de l'event ne doit JAMAIS être ce qui est stocké (stocké = SHA-256).
        String rawToken = evCap.getValue().rawToken();
        assertThat(rawToken).isNotBlank();
        assertThat(savedCap.getValue().getTokenHash())
            .isEqualTo(TenantAdminInviteService.sha256Hex(rawToken))
            .isNotEqualTo(rawToken);
        assertThat(evCap.getValue().tenantSlug()).isEqualTo("palmeraie");
    }

    @Test
    void createInvite_nullRole_defaultsToAdmin() {
        useFixedClock();
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(new Tenant(tenantId, "T", "t")));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            var dto = service.createInvite(tenantId, new CreateInviteDto("a@b.ma", null));
            assertThat(dto.role()).isEqualTo("admin");
        }
    }

    @Test
    void findRedeemable_pendingNotExpired_present_butExpiredOrAccepted_empty() {
        useFixedClock();
        UUID tenantId = UUID.randomUUID();
        String raw = "good-token";
        // pending + futur
        TenantAdminInvite ok = new TenantAdminInvite(UUID.randomUUID(), tenantId, "a@b.ma",
            TenantAdminInviteService.sha256Hex(raw), "admin", UUID.randomUUID(),
            fixed.instant().plusSeconds(3600));
        when(repo.findByTokenHash(TenantAdminInviteService.sha256Hex(raw))).thenReturn(Optional.of(ok));
        Optional<RedeemableInvite> res = service.findRedeemable(raw);
        assertThat(res).isPresent();
        assertThat(res.get().email()).isEqualTo("a@b.ma");

        // expirée
        ok.setStatus("pending");
        TenantAdminInvite expired = new TenantAdminInvite(UUID.randomUUID(), tenantId, "a@b.ma",
            TenantAdminInviteService.sha256Hex("exp"), "admin", null, fixed.instant().minusSeconds(1));
        when(repo.findByTokenHash(TenantAdminInviteService.sha256Hex("exp"))).thenReturn(Optional.of(expired));
        assertThat(service.findRedeemable("exp")).isEmpty();

        // déjà acceptée
        TenantAdminInvite accepted = new TenantAdminInvite(UUID.randomUUID(), tenantId, "a@b.ma",
            TenantAdminInviteService.sha256Hex("acc"), "admin", null, fixed.instant().plusSeconds(3600));
        accepted.setStatus("accepted");
        when(repo.findByTokenHash(TenantAdminInviteService.sha256Hex("acc"))).thenReturn(Optional.of(accepted));
        assertThat(service.findRedeemable("acc")).isEmpty();

        // inconnu / blank
        assertThat(service.findRedeemable("nope")).isEmpty();
        assertThat(service.findRedeemable("  ")).isEmpty();
    }

    @Test
    void redeem_marksAcceptedSingleUse() {
        useFixedClock();
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantAdminInvite invite = new TenantAdminInvite(id, UUID.randomUUID(), "a@b.ma",
            "hash", "admin", null, fixed.instant().plusSeconds(3600));
        when(repo.findById(id)).thenReturn(Optional.of(invite));
        service.redeem(id, userId);
        assertThat(invite.getStatus()).isEqualTo("accepted");
        assertThat(invite.getAcceptedUserId()).isEqualTo(userId);
        assertThat(invite.getAcceptedAt()).isEqualTo(fixed.instant());
        verify(repo).save(invite);
    }

    @Test
    void assignAdmin_idempotent() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(tenantAdminRepository.existsByTenantIdAndUserId(tenantId, userId)).thenReturn(true);
        service.assignAdmin(tenantId, userId, "admin", null);
        verify(tenantAdminRepository, never()).save(any());

        when(tenantAdminRepository.existsByTenantIdAndUserId(tenantId, userId)).thenReturn(false);
        service.assignAdmin(tenantId, userId, "admin", UUID.randomUUID());
        verify(tenantAdminRepository).save(any(TenantAdmin.class));
    }

    @Test
    void revokeInvite_accepted_throwsConflict() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        TenantAdminInvite accepted = new TenantAdminInvite(id, tenantId, "a@b.ma", "hash", "admin", null, Instant.now());
        accepted.setStatus("accepted");
        when(repo.findById(id)).thenReturn(Optional.of(accepted));
        assertThatThrownBy(() -> service.revokeInvite(tenantId, id)).isInstanceOf(ConflictException.class);
    }

    @Test
    void revokeInvite_pending_setsRevoked() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        TenantAdminInvite pending = new TenantAdminInvite(id, tenantId, "a@b.ma", "hash", "admin", null, Instant.now());
        when(repo.findById(id)).thenReturn(Optional.of(pending));
        service.revokeInvite(tenantId, id);
        assertThat(pending.getStatus()).isEqualTo("revoked");
        verify(repo).save(pending);
    }
}
