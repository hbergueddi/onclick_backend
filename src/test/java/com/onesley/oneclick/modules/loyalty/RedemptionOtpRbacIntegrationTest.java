package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC + flux E2E de l'OTP de conversion (Gap #2) sur la stack réelle.
 *
 * <p>Contrat : {@code CREATE:LOYALTY} pour demander un OTP (STAFF/admin) ; CLIENT → 403.
 * Enforcement snap2earn : une conversion > seuil sans OTP valide est refusée.
 *
 * <p>Isolation : restaurant + gain_rule jetables, comptes loyalty du resto purgés.
 */
class RedemptionOtpRbacIntegrationTest extends AbstractIntegrationTest {

    private UUID restaurantId;
    private UUID clientId;

    private static String sha256(String v) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @BeforeEach
    void setup() {
        UUID tenantId = UUID.fromString(jdbc.queryForObject("SELECT id::text FROM tenants LIMIT 1", String.class));
        clientId = UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id=u.role_id WHERE r.code='CLIENT' AND u.deleted_at IS NULL LIMIT 1",
            String.class));
        restaurantId = UUID.randomUUID();
        jdbc.update("INSERT INTO restaurants (id, tenant_id, name, city) VALUES (?, ?, ?, ?)",
            restaurantId, tenantId, "GAP2-Resto-" + restaurantId, "Casablanca");
        // gain_rule : taux 10%, seuil OTP bas (5 pts) pour déclencher facilement
        jdbc.update("INSERT INTO gain_rules (id, restaurant_id, conversion_rate, otp_required_above_pts) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), restaurantId, new java.math.BigDecimal("0.10"), 5);
    }

    @AfterEach
    void cleanup() {
        if (restaurantId != null) {
            jdbc.update("DELETE FROM redemption_otp_requests WHERE restaurant_id = ?", restaurantId);
            jdbc.update("DELETE FROM loyalty_transactions WHERE account_id IN (SELECT id FROM loyalty_accounts WHERE restaurant_id = ?)", restaurantId);
            jdbc.update("DELETE FROM loyalty_accounts WHERE restaurant_id = ?", restaurantId);
            jdbc.update("DELETE FROM gain_rules WHERE restaurant_id = ?", restaurantId);
            jdbc.update("DELETE FROM restaurants WHERE id = ?", restaurantId);
        }
        restaurantId = null;
        clientId = null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void staff_requestOtp_creates_pendingRow() {
        ResponseEntity<Map> resp = restTemplate.exchange(
            url("/api/loyalty/redemption-otp/request"), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "clientId", clientId.toString(),
                "restaurantId", restaurantId.toString(),
                "points", 300,
                "montant", 400,
                "discountDh", 30), adminBearer()),
            Map.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().get("requestId")).isNotNull();

        Integer pending = jdbc.queryForObject(
            "SELECT COUNT(*) FROM redemption_otp_requests WHERE client_id = ? AND restaurant_id = ? AND status = 'pending'",
            Integer.class, clientId, restaurantId);
        assertThat(pending).isEqualTo(1);
    }

    @Test
    void client_requestOtp_returns403() {
        int status = restTemplate.exchange(
            url("/api/loyalty/redemption-otp/request"), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "clientId", clientId.toString(), "restaurantId", restaurantId.toString(),
                "points", 10, "montant", 100, "discountDh", 10), bearerForRole("CLIENT")),
            String.class).getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void snap2earn_largeRedeem_requiresOtp_thenSucceedsWithCode() {
        String staff = adminBearer();
        String ticketRef = "GAP2-" + UUID.randomUUID();

        // Conversion de 10 pts > seuil 5 sans OTP → 400 OTP_REQUIRED (tx rollback)
        ResponseEntity<String> noOtp = restTemplate.exchange(
            url("/api/loyalty/snap2earn"), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "clientId", clientId.toString(), "restaurantId", restaurantId.toString(),
                "amount", 200, "ticketRef", ticketRef, "redeemPoints", 10), staff),
            String.class);
        assertThat(noOtp.getStatusCode().value()).isEqualTo(400);
        assertThat(noOtp.getBody()).contains("OTP_REQUIRED");

        // Insertion d'une demande OTP valide (code "654321")
        UUID otpId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO redemption_otp_requests
              (id, created_at, expires_at, client_id, restaurant_id, staff_id,
               points_requested, ticket_montant, estimated_discount_dh, code_hash, status, attempts)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', 0)
            """,
            otpId, java.sql.Timestamp.from(Instant.now()),
            java.sql.Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES)),
            clientId, restaurantId, SEED_SUPERADMIN_ID,
            10, new java.math.BigDecimal("200"), new java.math.BigDecimal("10"), sha256("654321"));

        // Re-scan avec OTP valide → succès (earn 20 + redeem 10 = solde 10)
        ResponseEntity<Map> withOtp = restTemplate.exchange(
            url("/api/loyalty/snap2earn"), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "clientId", clientId.toString(), "restaurantId", restaurantId.toString(),
                "amount", 200, "ticketRef", ticketRef, "redeemPoints", 10,
                "otpRequestId", otpId.toString(), "otpCode", "654321"), staff),
            Map.class);
        assertThat(withOtp.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) withOtp.getBody().get("pointsRedeemed")).intValue()).isEqualTo(10);

        // OTP consommé
        String otpStatus = jdbc.queryForObject(
            "SELECT status FROM redemption_otp_requests WHERE id = ?", String.class, otpId);
        assertThat(otpStatus).isEqualTo("consumed");
    }
}
