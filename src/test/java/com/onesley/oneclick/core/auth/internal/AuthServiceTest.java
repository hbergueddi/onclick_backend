package com.onesley.oneclick.core.auth.internal;

import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.core.identity.internal.UserService;
import com.onesley.oneclick.core.tenant.internal.TenantRepository;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.EmailNotVerifiedException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.JwtIssuer;
import com.onesley.oneclick.shared.events.OtpRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link AuthService} (L3 — core.auth, logique métier).
 * Couvre toutes les branches : login (succès / échecs / isolation tenant),
 * refresh (rotation), logout, revokeAllForUser, OTP request/verify.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepo;
    @Mock RefreshTokenRepository refreshRepo;
    @Mock OtpRequestRepository otpRepo;
    @Mock LoginHistoryRepository loginRepo;
    @Mock TenantRepository tenantRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtIssuer jwtIssuer;
    @Mock AccountActivationService accountActivationService;
    @Mock UserService userService;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks AuthService service;

    private User user;

    @BeforeEach
    void setup() {
        Role role = new Role(UUID.randomUUID(), "CLIENT", "Client");
        user = new User(UUID.randomUUID(), role, "a@b.ma", "$2a$hash", "Ada", "Lovelace");
    }

    private void stubTokenIssuance() {
        when(jwtIssuer.issueAccessToken(any(), anyString()))
            .thenReturn(new JwtIssuer.IssuedToken("access-jwt", Instant.now().plusSeconds(3600)));
        when(jwtIssuer.issueOpaqueRefreshToken()).thenReturn("refresh-opaque");
        when(jwtIssuer.getRefreshTtlSeconds()).thenReturn(2_592_000L);
    }

    // ─── acceptActivationInvite (Gap #10) ──────────────────────────────────────

    @Test
    void acceptActivationInvite_validToken_setsPassword_redeems_issuesSession() {
        UUID inviteId = UUID.randomUUID();
        when(accountActivationService.findRedeemable("raw-tok")).thenReturn(
            Optional.of(new AccountActivationService.RedeemableInvite(inviteId, user.getId(), user.getEmail())));
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("MyNewPass1")).thenReturn("$2a$encoded");
        stubTokenIssuance();

        AuthService.LoginResult r = service.acceptActivationInvite("raw-tok", "MyNewPass1");

        assertThat(r.accessToken()).isEqualTo("access-jwt");
        assertThat(r.userId()).isEqualTo(user.getId());
        assertThat(user.getPasswordHash()).isEqualTo("$2a$encoded"); // mot de passe écrit
        verify(accountActivationService).redeem(inviteId);            // single-use consommé
        verify(refreshRepo).save(any(RefreshToken.class));            // session ouverte
    }

    @Test
    void acceptActivationInvite_invalidToken_throwsBadRequest_noSession() {
        when(accountActivationService.findRedeemable("bad")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.acceptActivationInvite("bad", "MyNewPass1"))
            .isInstanceOf(BadRequestException.class);
        verify(accountActivationService, never()).redeem(any());
        verify(refreshRepo, never()).save(any());
    }

    // ─── login ───────────────────────────────────────────────────────────────

    @Test
    void login_userNotFound_throwsBadRequest_andLogsFailure() {
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login("a@b.ma", "pw", "ip", "dev"))
            .isInstanceOf(BadRequestException.class);
        verify(loginRepo).save(any(LoginHistory.class)); // audit même sur échec
        verify(refreshRepo, never()).save(any());
    }

    @Test
    void login_disabledAccount_throwsBadRequest() {
        ReflectionTestUtils.setField(user, "enabled", false);
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.login("a@b.ma", "pw", "ip", "dev"))
            .isInstanceOf(BadRequestException.class);
        verify(loginRepo).save(any(LoginHistory.class));
    }

    @Test
    void login_lockedAccount_throwsBadRequest() {
        ReflectionTestUtils.setField(user, "accountNonLocked", false);
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.login("a@b.ma", "pw", "ip", "dev"))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void login_badPassword_throwsBadRequest() {
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);
        assertThatThrownBy(() -> service.login("a@b.ma", "wrong", "ip", "dev"))
            .isInstanceOf(BadRequestException.class);
        verify(loginRepo).save(any(LoginHistory.class));
    }

    @Test
    void login_pendingEmailVerification_throwsEmailNotVerified_afterPasswordMatch() {
        ReflectionTestUtils.setField(user, "status", User.STATUS_PENDING_EMAIL_VERIFICATION);
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "$2a$hash")).thenReturn(true); // mot de passe correct

        assertThatThrownBy(() -> service.login("a@b.ma", "pw", "ip", "dev"))
            .isInstanceOf(EmailNotVerifiedException.class);  // 403 slug=email-not-verified
        verify(loginRepo).save(any(LoginHistory.class));     // audit (échec, success=false)
        verify(refreshRepo, never()).save(any());            // pas de session émise
    }

    @Test
    void login_success_issuesTokens_savesRefresh_updatesLastLogin() {
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "$2a$hash")).thenReturn(true);
        stubTokenIssuance();

        AuthService.LoginResult r = service.login("a@b.ma", "pw", "1.2.3.4", "iPhone");

        assertThat(r.accessToken()).isEqualTo("access-jwt");
        assertThat(r.refreshToken()).isEqualTo("refresh-opaque");
        assertThat(r.role()).isEqualTo("CLIENT");
        assertThat(r.email()).isEqualTo("a@b.ma");
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(refreshRepo).save(any(RefreshToken.class));
        verify(userRepo).save(user);
        verify(loginRepo).save(any(LoginHistory.class));
    }

    @Test
    void login_nullRole_defaultsToClient() {
        User noRole = new User(UUID.randomUUID(), null, "a@b.ma", "$2a$hash", "A", "B");
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.of(noRole));
        when(passwordEncoder.matches("pw", "$2a$hash")).thenReturn(true);
        stubTokenIssuance();

        AuthService.LoginResult r = service.login("a@b.ma", "pw", "ip", "dev");

        assertThat(r.role()).isEqualTo("CLIENT");
    }

    @Test
    void login_tenantMismatch_throwsForbidden() {
        UUID userTenant = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "tenantId", userTenant);
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "$2a$hash")).thenReturn(true);
        when(tenantRepo.findBySlug("homu"))
            .thenReturn(Optional.of(new Tenant(UUID.randomUUID(), "HOMU", "homu")));

        assertThatThrownBy(() -> service.login("a@b.ma", "pw", "ip", "dev", "homu"))
            .isInstanceOf(ForbiddenException.class);
        verify(loginRepo).save(any(LoginHistory.class));
        verify(refreshRepo, never()).save(any());
    }

    @Test
    void login_superadmin_bypassesTenantIsolation() {
        User admin = new User(UUID.randomUUID(),
            new Role(UUID.randomUUID(), "SUPERADMIN", "Super"), "a@b.ma", "$2a$hash", "A", "B");
        ReflectionTestUtils.setField(admin, "tenantId", UUID.randomUUID());
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("pw", "$2a$hash")).thenReturn(true);
        stubTokenIssuance();

        AuthService.LoginResult r = service.login("a@b.ma", "pw", "ip", "dev", "homu");

        assertThat(r.role()).isEqualTo("SUPERADMIN"); // pas de 403 malgré tenant différent
        verify(tenantRepo, never()).findBySlug(anyString()); // court-circuit cross-tenant
    }

    // ─── refresh ─────────────────────────────────────────────────────────────

    @Test
    void refresh_tokenNotFound_throwsBadRequest() {
        when(refreshRepo.findByToken("tok")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.refresh("tok")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void refresh_inactiveToken_throwsBadRequest() {
        RefreshToken revoked = new RefreshToken(UUID.randomUUID(), user, "tok", Instant.now().plusSeconds(60));
        revoked.revoke();
        when(refreshRepo.findByToken("tok")).thenReturn(Optional.of(revoked));
        assertThatThrownBy(() -> service.refresh("tok")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void refresh_userDisabled_throwsBadRequest() {
        ReflectionTestUtils.setField(user, "enabled", false);
        RefreshToken active = new RefreshToken(UUID.randomUUID(), user, "tok", Instant.now().plusSeconds(600));
        when(refreshRepo.findByToken("tok")).thenReturn(Optional.of(active));
        assertThatThrownBy(() -> service.refresh("tok")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void refresh_success_rotatesToken() {
        RefreshToken active = new RefreshToken(UUID.randomUUID(), user, "tok", Instant.now().plusSeconds(600));
        when(refreshRepo.findByToken("tok")).thenReturn(Optional.of(active));
        stubTokenIssuance();

        AuthService.LoginResult r = service.refresh("tok");

        assertThat(r.accessToken()).isEqualTo("access-jwt");
        assertThat(r.refreshToken()).isEqualTo("refresh-opaque");
        assertThat(active.getRevokedAt()).as("ancien token révoqué").isNotNull();
        verify(refreshRepo, times(2)).save(any(RefreshToken.class)); // révoqué + nouveau
    }

    // ─── logout / revokeAll ────────────────────────────────────────────────────

    @Test
    void logout_activeToken_revokesAndSaves() {
        RefreshToken active = new RefreshToken(UUID.randomUUID(), user, "tok", Instant.now().plusSeconds(600));
        when(refreshRepo.findByToken("tok")).thenReturn(Optional.of(active));
        service.logout("tok");
        assertThat(active.getRevokedAt()).isNotNull();
        verify(refreshRepo).save(active);
    }

    @Test
    void logout_alreadyRevoked_noSave() {
        RefreshToken revoked = new RefreshToken(UUID.randomUUID(), user, "tok", Instant.now().plusSeconds(600));
        revoked.revoke();
        when(refreshRepo.findByToken("tok")).thenReturn(Optional.of(revoked));
        service.logout("tok");
        verify(refreshRepo, never()).save(any());
    }

    @Test
    void logout_tokenNotFound_noop() {
        when(refreshRepo.findByToken("tok")).thenReturn(Optional.empty());
        service.logout("tok");
        verify(refreshRepo, never()).save(any());
    }

    @Test
    void revokeAllForUser_revokesOnlyActive_returnsCount() {
        RefreshToken active = new RefreshToken(UUID.randomUUID(), user, "t1", Instant.now().plusSeconds(600));
        RefreshToken already = new RefreshToken(UUID.randomUUID(), user, "t2", Instant.now().plusSeconds(600));
        already.revoke();
        when(refreshRepo.findAllByUserId(user.getId())).thenReturn(List.of(active, already));

        int n = service.revokeAllForUser(user.getId());

        assertThat(n).isEqualTo(1);
        verify(refreshRepo, times(1)).save(active);
    }

    // ─── OTP ────────────────────────────────────────────────────────────────

    @Test
    void requestOtp_userNotFound_throwsNotFound() {
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requestOtp("a@b.ma", "signup"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void requestOtp_success_savesOtp_returnsResult_publishesEvent() {
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.of(user));
        AuthService.OtpResult r = service.requestOtp("a@b.ma", "signup");
        assertThat(r.otpId()).isNotNull();
        assertThat(r.expiresAt()).isAfter(Instant.now());
        verify(otpRepo).save(any(OtpRequest.class));

        // L'event de livraison email est publié (frontière Modulith → OtpEmailListener).
        ArgumentCaptor<OtpRequestedEvent> ev = ArgumentCaptor.forClass(OtpRequestedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertThat(ev.getValue().email()).isEqualTo("a@b.ma");
        assertThat(ev.getValue().purpose()).isEqualTo("signup");
        assertThat(ev.getValue().code()).matches("\\d{6}");        // code 6 chiffres porté en clair
        assertThat(ev.getValue().tenantSlug()).isEqualTo("default"); // user sans tenant → branding OneClick
        assertThat(ev.getValue().tenantName()).isEqualTo("OneClick");
    }

    @Test
    void verifyOtp_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(otpRepo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verifyOtp(id, "123456")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void verifyOtp_alreadyVerified_throwsBadRequest() {
        OtpRequest otp = new OtpRequest(UUID.randomUUID(), user, "signup", "123456", Instant.now().plusSeconds(600));
        otp.markVerified();
        when(otpRepo.findById(otp.getId())).thenReturn(Optional.of(otp));
        assertThatThrownBy(() -> service.verifyOtp(otp.getId(), "123456")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void verifyOtp_expired_throwsBadRequest() {
        OtpRequest otp = new OtpRequest(UUID.randomUUID(), user, "signup", "123456", Instant.now().minusSeconds(10));
        when(otpRepo.findById(otp.getId())).thenReturn(Optional.of(otp));
        assertThatThrownBy(() -> service.verifyOtp(otp.getId(), "123456")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void verifyOtp_wrongCode_throwsBadRequest() {
        OtpRequest otp = new OtpRequest(UUID.randomUUID(), user, "signup", "123456", Instant.now().plusSeconds(600));
        when(otpRepo.findById(otp.getId())).thenReturn(Optional.of(otp));
        assertThatThrownBy(() -> service.verifyOtp(otp.getId(), "000000")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void verifyOtp_success_marksVerified() {
        OtpRequest otp = new OtpRequest(UUID.randomUUID(), user, "signup", "123456", Instant.now().plusSeconds(600));
        when(otpRepo.findById(otp.getId())).thenReturn(Optional.of(otp));

        boolean ok = service.verifyOtp(otp.getId(), "123456");

        assertThat(ok).isTrue();
        assertThat(otp.getVerifiedAt()).isNotNull();
        verify(otpRepo).save(otp);
    }

    @Test
    void verifyOtp_signupPendingUser_activatesAccount() {
        ReflectionTestUtils.setField(user, "status", User.STATUS_PENDING_EMAIL_VERIFICATION);
        OtpRequest otp = new OtpRequest(UUID.randomUUID(), user, OtpRequest.PURPOSE_SIGNUP, "123456", Instant.now().plusSeconds(600));
        when(otpRepo.findById(otp.getId())).thenReturn(Optional.of(otp));

        boolean ok = service.verifyOtp(otp.getId(), "123456");

        assertThat(ok).isTrue();
        assertThat(user.getStatus()).isEqualTo(User.STATUS_ACTIVE); // email vérifié → compte activé
        verify(userRepo).save(user);
    }

    @Test
    void verifyOtp_resetPasswordPurpose_doesNotTouchStatus() {
        ReflectionTestUtils.setField(user, "status", User.STATUS_ACTIVE);
        OtpRequest otp = new OtpRequest(UUID.randomUUID(), user, OtpRequest.PURPOSE_RESET_PASSWORD, "123456", Instant.now().plusSeconds(600));
        when(otpRepo.findById(otp.getId())).thenReturn(Optional.of(otp));

        service.verifyOtp(otp.getId(), "123456");

        assertThat(user.getStatus()).isEqualTo(User.STATUS_ACTIVE);
        verify(userRepo, never()).save(user); // reset_password ne réactive pas un compte actif
    }

    // ─── Mot de passe oublié — requestPasswordReset (Phase A) ───────────────────

    @Test
    void requestPasswordReset_existingUser_issuesResetOtp_publishesEvent() {
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.of(user));

        service.requestPasswordReset("a@b.ma");

        verify(otpRepo).save(any(OtpRequest.class));
        ArgumentCaptor<OtpRequestedEvent> cap = ArgumentCaptor.forClass(OtpRequestedEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().purpose()).isEqualTo(OtpRequest.PURPOSE_RESET_PASSWORD);
        assertThat(cap.getValue().email()).isEqualTo(user.getEmail());
        assertThat(cap.getValue().code()).matches("\\d{6}");
    }

    @Test
    void requestPasswordReset_unknownEmail_isSilent_noOtp_noEvent() {
        when(userRepo.findByEmailIgnoreCase("ghost@x.ma")).thenReturn(Optional.empty());

        service.requestPasswordReset("ghost@x.ma"); // anti-énumération : aucune exception

        verify(otpRepo, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─── Mot de passe oublié — resetPassword (Phase A) ──────────────────────────

    @Test
    void resetPassword_validCode_consumesOtp_setsPassword_revokesSessions() {
        OtpRequest otp = new OtpRequest(UUID.randomUUID(), user, OtpRequest.PURPOSE_RESET_PASSWORD,
            "123456", Instant.now().plusSeconds(600));
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.of(user));
        when(otpRepo.findTopByUserIdAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
            user.getId(), OtpRequest.PURPOSE_RESET_PASSWORD)).thenReturn(Optional.of(otp));

        service.resetPassword("a@b.ma", "123456", "BrandNewPass1");

        assertThat(otp.isVerified()).isTrue();                 // usage unique consommé
        verify(otpRepo).save(otp);
        verify(userService).resetPassword(user.getId(), "BrandNewPass1"); // set hash via identity
        verify(refreshRepo).findAllByUserId(user.getId());     // reconnexion forcée (révocation)
    }

    @Test
    void resetPassword_wrongCode_throws_noPasswordChange() {
        OtpRequest otp = new OtpRequest(UUID.randomUUID(), user, OtpRequest.PURPOSE_RESET_PASSWORD,
            "123456", Instant.now().plusSeconds(600));
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.of(user));
        when(otpRepo.findTopByUserIdAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
            user.getId(), OtpRequest.PURPOSE_RESET_PASSWORD)).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.resetPassword("a@b.ma", "000000", "BrandNewPass1"))
            .isInstanceOf(BadRequestException.class);

        verify(userService, never()).resetPassword(any(), anyString());
        verify(otpRepo, never()).save(any());
    }

    @Test
    void resetPassword_expiredCode_throws() {
        OtpRequest otp = new OtpRequest(UUID.randomUUID(), user, OtpRequest.PURPOSE_RESET_PASSWORD,
            "123456", Instant.now().minusSeconds(10)); // expiré
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.of(user));
        when(otpRepo.findTopByUserIdAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
            user.getId(), OtpRequest.PURPOSE_RESET_PASSWORD)).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.resetPassword("a@b.ma", "123456", "BrandNewPass1"))
            .isInstanceOf(BadRequestException.class);
        verify(userService, never()).resetPassword(any(), anyString());
    }

    @Test
    void resetPassword_noActiveOtp_throwsGeneric() {
        when(userRepo.findByEmailIgnoreCase("a@b.ma")).thenReturn(Optional.of(user));
        when(otpRepo.findTopByUserIdAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
            user.getId(), OtpRequest.PURPOSE_RESET_PASSWORD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("a@b.ma", "123456", "BrandNewPass1"))
            .isInstanceOf(BadRequestException.class);
        verify(userService, never()).resetPassword(any(), anyString());
    }

    @Test
    void resetPassword_unknownEmail_throwsGeneric_noOtpLookup() {
        when(userRepo.findByEmailIgnoreCase("ghost@x.ma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("ghost@x.ma", "123456", "BrandNewPass1"))
            .isInstanceOf(BadRequestException.class);
        verify(userService, never()).resetPassword(any(), anyString());
    }
}
