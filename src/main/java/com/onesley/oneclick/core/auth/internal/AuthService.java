package com.onesley.oneclick.core.auth.internal;

import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.core.identity.internal.RoleRepository;
import com.onesley.oneclick.core.identity.internal.UserService;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.core.tenant.api.TenantAdminInviteApi;
import com.onesley.oneclick.core.tenant.internal.TenantRepository;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.EmailNotVerifiedException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.JwtIssuer;
import com.onesley.oneclick.shared.events.OtpRequestedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

/**
 * Service authentification — §2 spec senior dev (Identity & Auth).
 *
 * <p>Implémente le flow complet attendu par la spec :
 * <ul>
 *   <li>{@link #login(String, String, String, String)} : BCrypt verify + issue access+refresh + log history</li>
 *   <li>{@link #refresh(String)} : rotation refresh token (révoque ancien, émet nouveau)</li>
 *   <li>{@link #logout(String)} : révoque le refresh token</li>
 *   <li>{@link #requestOtp(String, String)} / {@link #verifyOtp(UUID, String)} : flow OTP</li>
 * </ul>
 *
 * <p>Pattern sécurité :
 * <ul>
 *   <li>BCrypt strength 12 ({@code SecurityConfig.passwordEncoder()})</li>
 *   <li>Access token JWT HS256 — 1h TTL (§Spring Security)</li>
 *   <li>Refresh token opaque (32 bytes B64URL) — 30j TTL, stocké en DB pour révocation</li>
 *   <li>{@code LoginHistory} pour audit anti-brute-force</li>
 *   <li>OTP code 6 chiffres, TTL 10 min, usage unique (verified_at)</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RNG = new SecureRandom();

    private final UserRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final OtpRequestRepository otpRepo;
    private final LoginHistoryRepository loginRepo;
    private final TenantRepository tenantRepo;
    private final RoleRepository roleRepository;
    private final TenantAdminInviteApi tenantAdminInviteApi;
    private final AccountActivationService accountActivationService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final ApplicationEventPublisher eventPublisher;

    // ─── Login (email + password) ──────────────────────────────────────────────
    @Transactional
    public LoginResult login(String email, String rawPassword, String ipAddress, String device) {
        return login(email, rawPassword, ipAddress, device, null);
    }

    /**
     * Login avec tenant isolation : si {@code tenantSlug} fourni, refuse le login
     * (403) si le user appartient à un autre tenant. Permet à un même backend
     * Spring d'être utilisé par plusieurs apps whitelabel (OneClick, HOMU, PCC, ...)
     * sans qu'un user d'un tenant puisse se connecter à l'app d'un autre.
     */
    @Transactional
    public LoginResult login(String email, String rawPassword, String ipAddress, String device, String tenantSlug) {
        Optional<User> userOpt = userRepo.findByEmailIgnoreCase(email);
        User user = userOpt.orElse(null);

        boolean success = false;
        try {
            if (user == null) {
                throw new BadRequestException("Email ou mot de passe invalide");
            }
            if (!user.isEnabled() || !user.isAccountNonLocked()) {
                throw new BadRequestException("Compte désactivé ou verrouillé");
            }
            if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
                throw new BadRequestException("Email ou mot de passe invalide");
            }

            // Vérification email (P1 enrollment) — APRÈS le contrôle mot de passe pour
            // ne pas révéler l'état d'un compte à un attaquant qui ignore le mot de passe
            // (anti-énumération). 403 slug=email-not-verified → le client route vers l'OTP.
            if (User.STATUS_PENDING_EMAIL_VERIFICATION.equals(user.getStatus())) {
                throw new EmailNotVerifiedException(
                    "Votre adresse email n'est pas encore vérifiée. Saisissez le code reçu par email."
                );
            }

            // Tenant isolation — si le frontend transmet le slug de son app,
            // on vérifie que le user appartient bien à ce tenant.
            // SUPERADMIN/GROUP_ADMIN sont en cross-tenant par design (peuvent accéder
            // partout — on les filtrera côté UI si besoin).
            if (tenantSlug != null && !tenantSlug.isBlank()) {
                String roleCode = user.getRole() != null ? user.getRole().getCode() : null;
                boolean isCrossTenantRole = "SUPERADMIN".equals(roleCode) || "GROUP_ADMIN".equals(roleCode);
                if (!isCrossTenantRole) {
                    Tenant expectedTenant = tenantRepo.findBySlug(tenantSlug).orElse(null);
                    UUID expectedTenantId = expectedTenant != null ? expectedTenant.getId() : null;
                    if (expectedTenantId != null
                        && user.getTenantId() != null
                        && !expectedTenantId.equals(user.getTenantId())) {
                        throw new ForbiddenException(
                            "Ce compte n'est pas associé à l'application " + tenantSlug
                        );
                    }
                }
            }

            success = true;

            // OK : émettre tokens
            String roleCode = user.getRole() != null ? user.getRole().getCode() : "CLIENT";
            JwtIssuer.IssuedToken access = jwtIssuer.issueAccessToken(user.getId(), roleCode);
            String refreshOpaque = jwtIssuer.issueOpaqueRefreshToken();
            Instant refreshExp = Instant.now().plusSeconds(jwtIssuer.getRefreshTtlSeconds());

            RefreshToken rt = new RefreshToken(UUID.randomUUID(), user, refreshOpaque, refreshExp);
            refreshRepo.save(rt);

            user.setLastLoginAt(Instant.now());
            userRepo.save(user);

            return new LoginResult(
                access.token(), access.expiresAt(),
                refreshOpaque, refreshExp,
                user.getId(), user.getEmail(),
                user.getFirstName(), user.getLastName(),
                roleCode, user.isPasswordMustChange()
            );
        } finally {
            // Audit anti-brute-force (toujours, même sur échec)
            try {
                LoginHistory hist = new LoginHistory(
                    UUID.randomUUID(), user, ipAddress, device, success
                );
                loginRepo.save(hist);
            } catch (Exception e) {
                log.warn("LoginHistory persist failed (non-blocking) : {}", e.getMessage());
            }
        }
    }

    // ─── Acceptation invitation tenant-admin (E2 — magic-link, public gardé par token) ──
    /**
     * Accepte une invitation tenant-admin : crée (ou relie) le compte, l'assigne admin du
     * tenant, consomme l'invitation (single-use) et émet une session (access + refresh).
     *
     * <p>Sécurité : l'endpoint est PUBLIC (l'invité n'a pas encore de compte) — la
     * possession du token (haute entropie, single-use, expiry 7j, émis à cet email) tient
     * lieu de facteur d'authentification (équivalent magic-link). Pour un email DÉJÀ existant,
     * on N'écrase PAS le mot de passe (anti-takeover) : on assigne l'admin et on ouvre la
     * session du compte existant.</p>
     */
    @Transactional
    public LoginResult acceptTenantAdminInvite(String rawToken, String rawPassword,
                                               String firstName, String lastName, String phone) {
        TenantAdminInviteApi.RedeemableInvite invite = tenantAdminInviteApi.findRedeemable(rawToken)
            .orElseThrow(() -> new BadRequestException("Invitation invalide, expirée ou déjà utilisée."));

        User user = userRepo.findByEmailIgnoreCase(invite.email()).orElse(null);
        if (user == null) {
            Role role = roleRepository.findByCode("GROUP_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Rôle GROUP_ADMIN introuvable"));
            user = new User(UUID.randomUUID(), role, invite.email(),
                passwordEncoder.encode(rawPassword), firstName, lastName);
            user.setTenant(tenantRepo.getReferenceById(invite.tenantId()));
            if (phone != null && !phone.isBlank()) user.setPhone(phone.trim());
            user.setLanguage("fr");
            user = userRepo.save(user);
            log.info("[tenant-admin-invite] account created user={} tenant={}", user.getId(), invite.tenantId());
        }

        // Assigne admin (idempotent) + consomme l'invitation.
        tenantAdminInviteApi.assignAdmin(invite.tenantId(), user.getId(), invite.tenantRole(), invite.invitedBy());
        tenantAdminInviteApi.redeem(invite.inviteId(), user.getId());

        // Émet la session (même schéma que login()).
        String roleCode = user.getRole().getCode();
        JwtIssuer.IssuedToken access = jwtIssuer.issueAccessToken(user.getId(), roleCode);
        String refreshOpaque = jwtIssuer.issueOpaqueRefreshToken();
        Instant refreshExp = Instant.now().plusSeconds(jwtIssuer.getRefreshTtlSeconds());
        refreshRepo.save(new RefreshToken(UUID.randomUUID(), user, refreshOpaque, refreshExp));
        user.setLastLoginAt(Instant.now());
        userRepo.save(user);

        return new LoginResult(
            access.token(), access.expiresAt(),
            refreshOpaque, refreshExp,
            user.getId(), user.getEmail(),
            user.getFirstName(), user.getLastName(),
            roleCode, user.isPasswordMustChange());
    }

    /**
     * Gap #10 — Acceptation d'un lien d'<b>activation de compte membre</b> (premier login).
     *
     * <p>Quand un staff inscrit un nouveau membre, le compte est créé serveur-side avec un
     * mot de passe ALÉATOIRE que le membre ignore. Le membre reçoit un lien magique vers
     * {@code /accept-invite?token=...} ; cet endpoint définit son mot de passe et ouvre la
     * session — équivalent Spring du legacy Supabase ({@code AuthFlowGate} +
     * {@code CreatePasswordDialog} → {@code auth.updateUser({password})}).</p>
     *
     * <p>Sécurité : PUBLIC (le membre n'a pas de session). La possession du token (haute
     * entropie, single-use, expiry 7j, émis à cet email) tient lieu d'authentification.
     * Contrairement à l'invite tenant-admin, le compte existe DÉJÀ (créé à l'enrôlement) :
     * on se contente d'écrire le mot de passe choisi et de consommer l'invitation.</p>
     */
    @Transactional
    public LoginResult acceptActivationInvite(String rawToken, String rawPassword) {
        AccountActivationService.RedeemableInvite invite = accountActivationService.findRedeemable(rawToken)
            .orElseThrow(() -> new BadRequestException("Lien d'activation invalide, expiré ou déjà utilisé."));

        User user = userRepo.findById(invite.userId())
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new BadRequestException("Compte introuvable pour cette invitation."));

        // Écrit le mot de passe choisi par le membre (remplace le hash aléatoire d'enrôlement).
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        accountActivationService.redeem(invite.inviteId());

        // Émet la session (même schéma que login()).
        String roleCode = user.getRole() != null ? user.getRole().getCode() : "CLIENT";
        JwtIssuer.IssuedToken access = jwtIssuer.issueAccessToken(user.getId(), roleCode);
        String refreshOpaque = jwtIssuer.issueOpaqueRefreshToken();
        Instant refreshExp = Instant.now().plusSeconds(jwtIssuer.getRefreshTtlSeconds());
        refreshRepo.save(new RefreshToken(UUID.randomUUID(), user, refreshOpaque, refreshExp));
        user.setLastLoginAt(Instant.now());
        userRepo.save(user);

        log.info("[account-activation] accepted invite={} user={}", invite.inviteId(), user.getId());
        return new LoginResult(
            access.token(), access.expiresAt(),
            refreshOpaque, refreshExp,
            user.getId(), user.getEmail(),
            user.getFirstName(), user.getLastName(),
            roleCode, user.isPasswordMustChange());
    }

    // ─── Refresh (rotation) ────────────────────────────────────────────────────
    @Transactional
    public LoginResult refresh(String refreshTokenOpaque) {
        RefreshToken rt = refreshRepo.findByToken(refreshTokenOpaque)
            .orElseThrow(() -> new BadRequestException("Refresh token invalide"));
        if (!rt.isActive()) {
            throw new BadRequestException("Refresh token expiré ou révoqué");
        }
        User user = rt.getUser();
        if (user == null || !user.isEnabled()) {
            throw new BadRequestException("Compte inactif");
        }

        // Rotation : révoquer ancien, émettre nouveau
        rt.revoke();
        refreshRepo.save(rt);

        String roleCode = user.getRole() != null ? user.getRole().getCode() : "CLIENT";
        JwtIssuer.IssuedToken access = jwtIssuer.issueAccessToken(user.getId(), roleCode);
        String newRefresh = jwtIssuer.issueOpaqueRefreshToken();
        Instant newExp = Instant.now().plusSeconds(jwtIssuer.getRefreshTtlSeconds());
        RefreshToken next = new RefreshToken(UUID.randomUUID(), user, newRefresh, newExp);
        refreshRepo.save(next);

        return new LoginResult(
            access.token(), access.expiresAt(),
            newRefresh, newExp,
            user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), roleCode,
            user.isPasswordMustChange()
        );
    }

    // ─── Logout (revoke refresh) ───────────────────────────────────────────────
    @Transactional
    public void logout(String refreshTokenOpaque) {
        refreshRepo.findByToken(refreshTokenOpaque).ifPresent(rt -> {
            if (rt.getRevokedAt() == null) {
                rt.revoke();
                refreshRepo.save(rt);
            }
        });
    }

    @Transactional
    public int revokeAllForUser(UUID userId) {
        int n = 0;
        for (RefreshToken rt : refreshRepo.findAllByUserId(userId)) {
            if (rt.getRevokedAt() == null) {
                rt.revoke();
                refreshRepo.save(rt);
                n++;
            }
        }
        return n;
    }

    // ─── OTP (6 chiffres, 10 min) ──────────────────────────────────────────────
    @Transactional
    public OtpResult requestOtp(String email, String purpose) {
        User user = userRepo.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new NotFoundException("User", email));
        String code = String.format("%06d", RNG.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plusSeconds(600);
        OtpRequest otp = new OtpRequest(UUID.randomUUID(), user, purpose, code, expiresAt);
        otpRepo.save(otp);

        // Livraison du code par email (P1 enrollment). Frontière Modulith : core.auth ne peut
        // pas dépendre de core.email → on publie un event APRÈS commit, consommé par
        // OtpEmailListener (Resend). Le tenant porte le branding ; null pour un user global.
        String tenantSlug = "default";
        String tenantName = "OneClick";
        Tenant t = user.getTenant();
        if (t != null) {
            if (t.getSlug() != null && !t.getSlug().isBlank()) tenantSlug = t.getSlug();
            if (t.getName() != null && !t.getName().isBlank()) tenantName = t.getName();
        }
        eventPublisher.publishEvent(new OtpRequestedEvent(
            otp.getId(), user.getId(), user.getEmail(), user.getFirstName(),
            purpose, code, tenantSlug, tenantName, expiresAt, Instant.now()));

        log.info("OTP issued for {} (purpose={}) — delivery via OtpRequestedEvent (email/SMS par canal)", email, purpose);
        return new OtpResult(otp.getId(), expiresAt);
    }

    @Transactional
    public boolean verifyOtp(UUID otpId, String code) {
        OtpRequest otp = otpRepo.findById(otpId)
            .orElseThrow(() -> new NotFoundException("OtpRequest", otpId));
        if (otp.getVerifiedAt() != null) {
            throw new BadRequestException("Code déjà utilisé");
        }
        if (Instant.now().isAfter(otp.getExpiresAt())) {
            throw new BadRequestException("Code expiré");
        }
        if (!otp.getCode().equals(code)) {
            throw new BadRequestException("Code invalide");
        }
        otp.markVerified();
        otpRepo.save(otp);

        // Activation du compte au signup (P1 enrollment) : un OTP signup/verify_email validé
        // lève le gate email du compte (pending_email_verification → active) pour autoriser le
        // login. Pas d'éviction de cache nécessaire : un compte pending n'a jamais été authentifié
        // (login gaté), donc jamais chargé dans le cache userDetails ; il le sera frais au 1er login.
        if ((OtpRequest.PURPOSE_SIGNUP.equals(otp.getPurpose())
                || OtpRequest.PURPOSE_VERIFY_EMAIL.equals(otp.getPurpose()))) {
            User u = otp.getUser();
            if (u != null && User.STATUS_PENDING_EMAIL_VERIFICATION.equals(u.getStatus())) {
                u.setStatus(User.STATUS_ACTIVE);
                userRepo.save(u);
                log.info("[otp/verify] account activated user={} (email verified)", u.getId());
            }
        }
        return true;
    }

    // ─── Mot de passe oublié (Phase A — OTP code par email, reconnexion forcée) ──
    /**
     * Étape 1 : l'utilisateur demande un code de réinitialisation pour son email.
     *
     * <p><b>Anti-énumération</b> : ne révèle JAMAIS si l'email correspond à un compte.
     * Si le compte existe → génère un OTP {@code reset_password} (envoyé par email via
     * {@link OtpRequestedEvent} → OtpEmailListener/Resend). Sinon → no-op silencieux.
     * Le contrôleur renvoie un 202 générique dans tous les cas.</p>
     */
    @Transactional
    public void requestPasswordReset(String email) {
        if (userRepo.findByEmailIgnoreCase(email).isEmpty()) {
            log.info("[forgot-password] aucun compte pour cet email — no-op (anti-énumération)");
            return;
        }
        // Réutilise la génération OTP existante (persistance + publication de l'event email).
        requestOtp(email, OtpRequest.PURPOSE_RESET_PASSWORD);
    }

    /**
     * Étape 2 : vérifie le code reçu par email et réinitialise le mot de passe.
     *
     * <p>Le client ne connaît pas l'{@code otpId} (l'étape 1 ne le renvoie pas — anti-énum) :
     * on retrouve le dernier OTP {@code reset_password} non vérifié du user (via email) et on
     * compare le code. <b>Anti-énumération</b> : compte inexistant / aucun OTP / expiré / code
     * faux → même erreur générique {@code "Code invalide ou expiré"} (BadRequest 400).</p>
     *
     * <p>Sécurité : OTP usage-unique ({@code verifiedAt}), atomique ({@code @Transactional} —
     * un échec en aval rollback le {@code markVerified}, donc le code reste utilisable) ; refus
     * de réutiliser l'ancien mot de passe ({@link UserService#resetPassword}) ; <b>reconnexion
     * forcée</b> : toutes les sessions (refresh tokens) sont révoquées, aucun token renvoyé.</p>
     */
    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        User user = userRepo.findByEmailIgnoreCase(email).orElse(null);
        OtpRequest otp = (user == null) ? null
            : otpRepo.findTopByUserIdAndPurposeAndVerifiedAtIsNullOrderByCreatedAtDesc(
                  user.getId(), OtpRequest.PURPOSE_RESET_PASSWORD).orElse(null);

        if (user == null || otp == null || otp.isExpired() || !otp.getCode().equals(code)) {
            throw new BadRequestException("Code invalide ou expiré");
        }

        otp.markVerified();           // usage unique (rollback automatique si la suite échoue)
        otpRepo.save(otp);
        userService.resetPassword(user.getId(), newPassword); // set hash + evict cache + refuse réutilisation
        int revoked = revokeAllForUser(user.getId());         // reconnexion forcée : tue toutes les sessions
        log.info("[reset-password] mot de passe réinitialisé user={} ({} sessions révoquées)", user.getId(), revoked);
    }

    // ─── DTOs ──────────────────────────────────────────────────────────────────
    public record LoginResult(
        String accessToken, Instant accessExpiresAt,
        String refreshToken, Instant refreshExpiresAt,
        UUID userId, String email, String firstName, String lastName, String role,
        // BE-3 — true si le compte doit définir un nouveau mot de passe au 1er login (mdp temporaire).
        boolean passwordMustChange
    ) {}

    public record OtpResult(UUID otpId, Instant expiresAt) {}
}
