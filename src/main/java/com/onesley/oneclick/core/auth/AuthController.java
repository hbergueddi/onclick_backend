package com.onesley.oneclick.core.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;
import com.onesley.oneclick.core.auth.internal.AuthService;
import com.onesley.oneclick.core.auth.internal.AuthService.LoginResult;
import com.onesley.oneclick.core.auth.internal.AuthService.OtpResult;
import lombok.RequiredArgsConstructor;

/**
 * Authentification — POST /api/auth/* — Phase 4 §2 spec senior dev.
 *
 * <p>Endpoints publics (whitelist dans {@code SecurityConfig}) :
 * <ul>
 *   <li>POST /api/auth/login</li>
 *   <li>POST /api/auth/refresh</li>
 *   <li>POST /api/auth/logout</li>
 *   <li>POST /api/auth/otp/request</li>
 *   <li>POST /api/auth/otp/verify</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Authentification login/refresh/logout/OTP (§2 spec senior)")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login email + password → access token JWT HS256 + refresh token opaque (avec tenant isolation optionnelle)")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto body, HttpServletRequest req) {
        String ip = clientIp(req);
        String device = req.getHeader("User-Agent");
        // tenantSlug optionnel : si présent, refuse les users d'autres tenants (403 propre).
        // Permet à un même backend de servir plusieurs apps whitelabel (OneClick/HOMU/PCC/...).
        AuthService.LoginResult r = authService.login(
            body.email(), body.password(), ip, device, body.tenantSlug()
        );
        return ResponseEntity.ok(LoginResponseDto.from(r));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotation refresh token → nouveau couple access+refresh")
    public ResponseEntity<LoginResponseDto> refresh(@Valid @RequestBody RefreshRequestDto body) {
        AuthService.LoginResult r = authService.refresh(body.refreshToken());
        return ResponseEntity.ok(LoginResponseDto.from(r));
    }

    @PostMapping("/logout")
    @Operation(summary = "Révoque le refresh token (204)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequestDto body) {
        authService.logout(body.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/otp/request")
    @Operation(summary = "Demande un code OTP 6 chiffres (signup, reset_password, 2fa, etc.) → otpId + expiresAt")
    public ResponseEntity<OtpRequestResponseDto> requestOtp(@Valid @RequestBody OtpRequestDto body) {
        AuthService.OtpResult r = authService.requestOtp(body.email(), body.purpose());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(new OtpRequestResponseDto(r.otpId(), r.expiresAt()));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Vérifie le code OTP (200 si OK, 400 sinon)")
    public ResponseEntity<OtpVerifyResponseDto> verifyOtp(@Valid @RequestBody OtpVerifyDto body) {
        authService.verifyOtp(body.otpId(), body.code());
        return ResponseEntity.ok(new OtpVerifyResponseDto(true));
    }

    @PostMapping("/accept-tenant-admin-invite")
    @Operation(
        summary = "E2 — accepte une invitation tenant-admin (PUBLIC, gardé par token) → crée le mot de passe + session",
        description = "Lien magique 1ʳᵉ connexion : crée/relie le compte, l'assigne admin du tenant, " +
                      "consomme l'invitation (single-use) et renvoie access+refresh JWT. Token invalide/expiré → 400."
    )
    public ResponseEntity<LoginResponseDto> acceptTenantAdminInvite(@Valid @RequestBody AcceptInviteRequestDto body) {
        AuthService.LoginResult r = authService.acceptTenantAdminInvite(
            body.token(), body.password(), body.firstName(), body.lastName(), body.phone());
        return ResponseEntity.ok(LoginResponseDto.from(r));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private static String clientIp(HttpServletRequest req) {
        String h = req.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) return h.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────────
    public record LoginRequestDto(
        @Email @NotBlank String email,
        @NotBlank String password,
        /** Tenant slug optionnel — si présent, vérifie que le user appartient à
         *  ce tenant (sinon 403). Permet multi-app whitelabel (OneClick/HOMU/PCC).
         *  Roles cross-tenant (SUPERADMIN, GROUP_ADMIN) ne sont jamais bloqués. */
        String tenantSlug
    ) {}

    public record RefreshRequestDto(
        @NotBlank String refreshToken
    ) {}

    public record OtpRequestDto(
        @Email @NotBlank String email,
        @NotBlank @Pattern(regexp = "^(signup|reset_password|verify_phone|verify_email|2fa|redemption)$") String purpose
    ) {}

    public record OtpVerifyDto(
        @jakarta.validation.constraints.NotNull UUID otpId,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "code must be 6 digits") String code
    ) {}

    /** Acceptation invitation tenant-admin (E2). Le {@code token} clair vient du lien email. */
    public record AcceptInviteRequestDto(
        @NotBlank String token,
        @NotBlank @jakarta.validation.constraints.Size(min = 8, max = 100) String password,
        @NotBlank @jakarta.validation.constraints.Size(max = 128) String firstName,
        @NotBlank @jakarta.validation.constraints.Size(max = 128) String lastName,
        @jakarta.validation.constraints.Size(max = 64) String phone
    ) {}

    public record LoginResponseDto(
        String accessToken, Instant accessExpiresAt,
        String refreshToken, Instant refreshExpiresAt,
        String tokenType,
        UUID userId, String email, String firstName, String lastName, String role
    ) {
        public static LoginResponseDto from(AuthService.LoginResult r) {
            return new LoginResponseDto(
                r.accessToken(), r.accessExpiresAt(),
                r.refreshToken(), r.refreshExpiresAt(),
                "Bearer",
                r.userId(), r.email(), r.firstName(), r.lastName(), r.role()
            );
        }
    }

    public record OtpRequestResponseDto(UUID otpId, Instant expiresAt) {}
    public record OtpVerifyResponseDto(boolean verified) {}
}
