package com.onesley.oneclick.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Emetteur JWT HS256 — partenaire du {@link JwtConfig} (NimbusJwtDecoder).
 *
 * <p>Émet les tokens utilisés par {@code AuthService.login()} / {@code refresh()}.
 * Format Supabase-compatible (claims {@code iss}, {@code sub}, {@code iat}, {@code exp}, {@code role}).
 *
 * <p>Indépendant de Nimbus côté émission (HMAC pur Java) pour éviter un cycle de
 * dépendances avec le starter oauth2-resource-server qui occupe déjà l'écosystème
 * Nimbus côté validation.
 */
@Component
public class JwtIssuer {

    private final byte[] secretBytes;
    private final String issuer;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public JwtIssuer(
        @Value("${app.security.jwt-secret:onesley-oneclick-dev-secret-256-bits-minimum-length-required}") String secret,
        @Value("${app.security.jwt-issuer:oneclick-enterprise}") String issuer,
        @Value("${app.security.access-token-ttl-seconds:3600}") long accessTtlSeconds,
        @Value("${app.security.refresh-token-ttl-seconds:2592000}") long refreshTtlSeconds  // 30 days
    ) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public IssuedToken issueAccessToken(UUID userId, String roleCode) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(accessTtlSeconds);
        String token = signHs256(userId.toString(), roleCode, now, exp);
        return new IssuedToken(token, exp);
    }

    public String issueOpaqueRefreshToken() {
        // Refresh token = chaîne opaque aléatoire (pas un JWT). Stockée en DB pour révocation.
        byte[] raw = new byte[32];
        new java.security.SecureRandom().nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public long getRefreshTtlSeconds() {
        return refreshTtlSeconds;
    }

    // ─── Internals ─────────────────────────────────────────────────────────────

    private String signHs256(String sub, String role, Instant iat, Instant exp) {
        String header = base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64UrlEncode(String.format(
            "{\"iss\":\"%s\",\"sub\":\"%s\",\"iat\":%d,\"exp\":%d,\"role\":\"%s\"}",
            escape(issuer), sub, iat.getEpochSecond(), exp.getEpochSecond(), escape(role == null ? "authenticated" : role)
        ));
        String unsigned = header + "." + payload;
        String signature = base64UrlEncode(hmacSha256(unsigned.getBytes(StandardCharsets.UTF_8)));
        return unsigned + "." + signature;
    }

    private byte[] hmacSha256(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String base64UrlEncode(String s) {
        return base64UrlEncode(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record IssuedToken(String token, Instant expiresAt) {}
}
