package com.onesley.oneclick.modules.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration Gap #8 — activation d'un parrainage par CODE (PATCH /api/social/referrals/activate).
 *
 * <p>Le bug live : le FE appelait cet endpoint (par code) qui n'existait pas → 404. On vérifie ici
 * que le contrat est rétabli : endpoint présent (pas 404), CLIENT autorisé (UPDATE:COMMUNITY),
 * E2E happy-path (référral activé + auto-amitié), et erreurs métier (code invalide → 400, doublon → 409).
 */
class ReferralByCodeIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private UUID filleulId;
    private UUID referrerId;
    private String savedReferrerCode;
    private String code;

    @BeforeEach
    void setup() {
        // Filleul = un CLIENT réel ; parrain = un autre user (≠ filleul).
        filleulId = UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id=u.role_id "
            + "WHERE r.code='CLIENT' AND u.deleted_at IS NULL LIMIT 1", String.class));
        referrerId = UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM users WHERE id <> ? AND deleted_at IS NULL LIMIT 1", String.class, filleulId));
        // Sauvegarde + pose un code de parrainage unique sur le parrain (restauré en teardown).
        savedReferrerCode = jdbc.queryForObject("SELECT referral_code FROM users WHERE id = ?", String.class, referrerId);
        code = "GAP8-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("UPDATE users SET referral_code = ? WHERE id = ?", code, referrerId);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM referrals WHERE referred_user_id = ? AND referrer_id = ?", filleulId, referrerId);
        jdbc.update("DELETE FROM friendships WHERE (user1_id = ? AND user2_id = ?) OR (user1_id = ? AND user2_id = ?)",
            filleulId, referrerId, referrerId, filleulId);
        jdbc.update("UPDATE users SET referral_code = ? WHERE id = ?", savedReferrerCode, referrerId);
    }

    @Test
    void noJwt_returns401() {
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/social/referrals/activate"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("referralCode", code), null), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void client_invalidCode_returns400_not404() {
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/social/referrals/activate"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("referralCode", "DOES-NOT-EXIST-" + UUID.randomUUID()), bearerForRole("CLIENT")),
            String.class);
        // 400 (et surtout PAS 404) → l'endpoint existe et route bien (correction du bug de contrat).
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void client_validCode_activates_andCreatesFriendship_thenDuplicate409() throws Exception {
        String filleulBearer = jwtIssuer.issueAccessToken(filleulId, "CLIENT").token();

        ResponseEntity<Map> resp = restTemplate.exchange(
            url("/api/social/referrals/activate"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("referralCode", code), filleulBearer), Map.class);
        assertThat(resp.getStatusCode().value())
            .as("activation par code — reçu %s, body=%s", resp.getStatusCode(), resp.getBody())
            .isEqualTo(200);
        assertThat(resp.getBody().get("status")).isEqualTo("activated");

        // DB : référral actif + amitié acceptée filleul↔parrain.
        Integer referrals = jdbc.queryForObject(
            "SELECT COUNT(*) FROM referrals WHERE referrer_id = ? AND referred_user_id = ? AND status = 'activated'",
            Integer.class, referrerId, filleulId);
        assertThat(referrals).isEqualTo(1);
        Integer friendships = jdbc.queryForObject(
            "SELECT COUNT(*) FROM friendships WHERE status='accepted' AND "
            + "((user1_id=? AND user2_id=?) OR (user1_id=? AND user2_id=?))",
            Integer.class, filleulId, referrerId, referrerId, filleulId);
        assertThat(friendships).isEqualTo(1);

        // Doublon : 2e activation du même code → 409.
        int dup = restTemplate.exchange(
            url("/api/social/referrals/activate"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("referralCode", code), filleulBearer), String.class).getStatusCode().value();
        assertThat(dup).isEqualTo(409);
    }
}
