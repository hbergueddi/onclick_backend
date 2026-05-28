package com.onesley.oneclick.modules.loyalty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/loyalty} extensions : ratings/scores/ai-usage/restitutions/tier-status/distributions. */
class LoyaltyExtensionFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String userId() { return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class); }
    private String restaurantId() { return jdbc.queryForObject("SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class); }

    /** User CLIENT jetable (évite de muter le rating d'un user seed). */
    private String createUser(String admin) throws Exception {
        String roleId = jdbc.queryForObject("SELECT id::text FROM roles WHERE code='CLIENT' LIMIT 1", String.class);
        var r = restTemplate.exchange(url("/api/users"), HttpMethod.POST, jsonJwtEntity(Map.of(
            "roleId", roleId, "email", "l4-lext-" + UUID.randomUUID() + "@x.ma",
            "password", "password1234", "firstName", "L4", "lastName", "Lext"), admin), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("création user jetable").isTrue();
        return om.readTree(r.getBody()).get("id").asText();
    }

    @Test
    void reads_200() {
        String admin = adminBearer();
        String uid = userId(), rid = restaurantId();
        for (String path : new String[]{
            "/api/loyalty/ratings/by-user/" + uid,
            "/api/loyalty/scores/by-user/" + uid,
            "/api/loyalty/ai-usage/by-user/" + uid,
            "/api/loyalty/restitutions/by-restaurant/" + rid,
            "/api/loyalty/tier-status/by-restaurant/" + rid,
            "/api/loyalty/expired-points/admin",
            "/api/loyalty/point-distributions",
            "/api/loyalty/tier-distribution"}) {
            assertThat(restTemplate.exchange(url(path), HttpMethod.GET, jwtEntity(admin), String.class)
                .getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void scoreConfig_getAndPatch() throws Exception {
        String admin = adminBearer();
        // GET singleton (seedé par V52) — admin-only (VIEW:ANALYTICS).
        ResponseEntity<String> get = restTemplate.exchange(url("/api/loyalty/score-config"),
            HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(get.getBody()).has("minReservations")).isTrue();
        // PATCH partiel (UPDATE:ANALYTICS).
        ResponseEntity<String> patch = restTemplate.exchange(url("/api/loyalty/score-config"),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("minReservations", 4, "fenetreMois", 9), admin), String.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(patch.getBody()).get("minReservations").asInt()).isEqualTo(4);
        assertThat(om.readTree(patch.getBody()).get("fenetreMois").asInt()).isEqualTo(9);
    }

    @Test
    void aiUsage_increment_2xx() {
        assertThat(restTemplate.exchange(url("/api/loyalty/ai-usage/by-user/" + userId() + "/increment"),
            HttpMethod.POST, jwtEntity(adminBearer()), String.class).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void reads_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/loyalty/point-distributions"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void pointsEconomy_adminOk_shape_restaurateur403_anon401() throws Exception {
        // Admin (VIEW:ANALYTICS) → 200 + shape complet de l'agrégat.
        var ok = restTemplate.exchange(url("/api/loyalty/points-economy"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = om.readTree(ok.getBody());
        assertThat(body.has("emitted")).isTrue();
        assertThat(body.has("consumed")).isTrue();
        assertThat(body.has("expired")).isTrue();
        assertThat(body.get("byType").isArray()).isTrue();
        assertThat(body.get("monthly").isArray()).isTrue();
        // Invariant : available = emitted - consumed - expired.
        assertThat(body.get("available").asLong())
            .isEqualTo(body.get("emitted").asLong() - body.get("consumed").asLong() - body.get("expired").asLong());
        // VIEW:ANALYTICS réservé SUPERADMIN → RESTAURATEUR 403.
        assertThat(restTemplate.exchange(url("/api/loyalty/points-economy"),
            HttpMethod.GET, jwtEntity(bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Anonyme → 401.
        assertThat(restTemplate.exchange(url("/api/loyalty/points-economy"),
            HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── Écritures jusqu'ici non couvertes au L4 (recordRating / createRestitution) ───

    @Test
    void recordRating_create() throws Exception {
        String admin = adminBearer();
        String uid = createUser(admin); // user jetable → pas de mutation de rating seed
        assertThat(restTemplate.exchange(url("/api/loyalty/ratings"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userId", uid, "delta", 0.1, "reason", "L4 honorée"), admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();
        jdbc.update("DELETE FROM client_ratings WHERE user_id = ?::uuid", UUID.fromString(uid)); // self-clean
        restTemplate.exchange(url("/api/users/" + uid), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void createRestitution_create() throws Exception {
        String admin = adminBearer();
        ResponseEntity<String> r = restTemplate.exchange(url("/api/loyalty/restitutions"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restaurantId(), "amount", 50.0, "points", 100, "reason", "L4 restitution"), admin), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(r.getBody()).get("id").asText();
        jdbc.update("DELETE FROM wallet_transactions WHERE reference_id = ?::uuid", UUID.fromString(id)); // side-effect éventuel
        jdbc.update("DELETE FROM restaurant_restitutions WHERE id = ?::uuid", UUID.fromString(id));
    }
}
