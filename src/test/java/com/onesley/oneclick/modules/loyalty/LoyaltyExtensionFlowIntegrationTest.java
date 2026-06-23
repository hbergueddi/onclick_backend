package com.onesley.oneclick.modules.loyalty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.modules.loyalty.internal.LoyaltyExtensionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/loyalty} extensions : ratings/scores/ai-usage/restitutions/tier-status/distributions. */
class LoyaltyExtensionFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    @Autowired LoyaltyExtensionService loyaltyExtensionService;
    private String userId() { return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class); }
    private String restaurantId() { return jdbc.queryForObject("SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class); }

    @Test
    void recordRating_ghostReservation_skipsWithoutFkViolation() {
        // Garde-fou FK (client_ratings.reservation_id → reservations) : réservation absente
        // (ex: event Modulith rejoué après suppression) → rating ignoré, pas de violation FK, retour null.
        UUID ghostReservation = UUID.randomUUID();
        var dto = loyaltyExtensionService.recordRating(
            UUID.fromString(userId()), ghostReservation, new BigDecimal("-0.5"), "no_show");
        assertThat(dto).as("résa absente → rating ignoré").isNull();
        Long rows = jdbc.queryForObject(
            "SELECT count(*) FROM client_ratings WHERE reservation_id = ?::uuid", Long.class, ghostReservation.toString());
        assertThat(rows).isZero();
    }

    @Test
    void restitutionsByRestaurants_admin200_restaurateurScoped() {
        String admin = adminBearer();
        String rid = restaurantId();
        // Admin → 200 (bypass ABAC), réponse tableau.
        ResponseEntity<String> adminRes = restTemplate.exchange(
            url("/api/loyalty/restitutions/by-restaurants?restaurantIds=" + rid),
            HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(adminRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminRes.getBody()).startsWith("[");

        // RESTAURATEUR (non-admin) : 200 sur son resto, 403 sur un resto étranger (ABAC par-id).
        java.util.Map<String, Object> row = jdbc.queryForMap(
            "SELECT u.id::text AS uid, rs.restaurant_id::text AS rid "
            + "FROM users u JOIN roles r ON r.id = u.role_id "
            + "JOIN restaurant_staffs rs ON rs.user_id = u.id "
            + "WHERE r.code = 'RESTAURATEUR' AND rs.deleted_at IS NULL AND u.deleted_at IS NULL "
            + "ORDER BY u.id LIMIT 1");
        String ownerId = (String) row.get("uid");
        String ownedRid = (String) row.get("rid");
        String ownerBearer = jwtIssuer.issueAccessToken(UUID.fromString(ownerId), "RESTAURATEUR").token();
        String foreignRid = jdbc.queryForObject(
            "SELECT id::text FROM restaurants WHERE deleted_at IS NULL "
            + "AND id NOT IN (SELECT restaurant_id FROM restaurant_staffs WHERE user_id = ?::uuid AND deleted_at IS NULL) "
            + "LIMIT 1", String.class, ownerId);

        assertThat(restTemplate.exchange(url("/api/loyalty/restitutions/by-restaurants?restaurantIds=" + ownedRid),
            HttpMethod.GET, jwtEntity(ownerBearer), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/loyalty/restitutions/by-restaurants?restaurantIds=" + ownedRid + "," + foreignRid),
            HttpMethod.GET, jwtEntity(ownerBearer), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void restitutionsByRestaurants_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/loyalty/restitutions/by-restaurants?restaurantIds=" + restaurantId()),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

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

    /**
     * #4 — PointDistributionDto expose désormais remainingPoints (= solde courant
     * loyalty_accounts.balance) et creditedBy (= loyalty_transactions.created_by).
     * On sème un compte + une transaction earn connus, puis on filtre par client.
     */
    @Test
    void pointDistributions_exposeRemainingPointsAndCreditedBy() throws Exception {
        String admin = adminBearer();
        String client = createUser(admin);            // CLIENT jetable (pas de compte préexistant)
        String rid = restaurantId();
        String creator = SEED_SUPERADMIN_ID.toString();
        String accId = UUID.randomUUID().toString();
        // tenant_id auto-rempli par trigger trg_loyalty_accounts_tenant_id (V10).
        jdbc.update("INSERT INTO loyalty_accounts (id, client_id, restaurant_id, balance) VALUES (?::uuid, ?::uuid, ?::uuid, 250)",
            accId, client, rid);
        jdbc.update("INSERT INTO loyalty_transactions (account_id, type, points, reason, created_by) VALUES (?::uuid, 'earn', 40, 'snap2earn|TEST4', ?::uuid)",
            accId, creator);
        try {
            var res = restTemplate.exchange(url("/api/loyalty/point-distributions?userId=" + client),
                HttpMethod.GET, jwtEntity(admin), String.class);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            var arr = om.readTree(res.getBody());
            assertThat(arr.isArray()).isTrue();
            assertThat(arr).hasSizeGreaterThanOrEqualTo(1);
            var row = arr.get(0);
            assertThat(row.get("remainingPoints").asInt()).isEqualTo(250); // = balance du compte
            assertThat(row.get("creditedBy").asText()).isEqualTo(creator);
        } finally {
            jdbc.update("DELETE FROM loyalty_transactions WHERE account_id = ?::uuid", accId);
            jdbc.update("DELETE FROM loyalty_accounts WHERE id = ?::uuid", accId);
        }
    }

    /**
     * B2 — l'agrégat crédit resto remplace le pull de 10k lignes. On sème un compte +
     * txns connus et on vérifie les deltas (resto partagé → baseline) + byMember + le
     * plafond @Max(10000) sur /point-distributions (Spring Boot 4 valide les params).
     */
    @Test
    void restaurantCreditSummary_aggregatesAndCapsLimit() throws Exception {
        String admin = adminBearer();
        // Anon → 401 (filtre sécurité, avant l'ABAC).
        assertThat(restTemplate.exchange(url("/api/loyalty/restaurant-credit-summary/" + restaurantId()),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String client = createUser(admin);  // CLIENT jetable, sert aussi de created_by unique
        String rid = restaurantId();
        String accId = UUID.randomUUID().toString();

        var before = om.readTree(restTemplate.exchange(url("/api/loyalty/restaurant-credit-summary/" + rid),
            HttpMethod.GET, jwtEntity(admin), String.class).getBody());
        long accordeBefore = before.get("creditAccorde").asLong();
        long dispoBefore = before.get("creditDispo").asLong();
        long consoBefore = before.get("creditConsomme").asLong();

        // tenant_id auto-rempli par trigger (V10).
        jdbc.update("INSERT INTO loyalty_accounts (id, client_id, restaurant_id, balance) VALUES (?::uuid, ?::uuid, ?::uuid, 900000)",
            accId, client, rid);
        jdbc.update("INSERT INTO loyalty_transactions (account_id, type, points, created_by) VALUES (?::uuid, 'earn', 900000, ?::uuid)",
            accId, client);
        jdbc.update("INSERT INTO loyalty_transactions (account_id, type, points, created_by) VALUES (?::uuid, 'spend', -100000, ?::uuid)",
            accId, client);
        try {
            var res = restTemplate.exchange(url("/api/loyalty/restaurant-credit-summary/" + rid),
                HttpMethod.GET, jwtEntity(admin), String.class);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            var node = om.readTree(res.getBody());
            assertThat(node.get("creditAccorde").asLong() - accordeBefore).isEqualTo(900000L);   // SUM(points>0)
            assertThat(node.get("creditDispo").asLong() - dispoBefore).isEqualTo(900000L);        // SUM(balance)
            assertThat(node.get("creditConsomme").asLong() - consoBefore).isEqualTo(100000L);     // SUM(spend → -points)
            boolean found = false;
            for (var m : node.get("byMember"))
                if (client.equals(m.get("userId").asText()) && m.get("points").asLong() == 900000L) found = true;
            assertThat(found).as("byMember contient le membre créateur semé").isTrue();

            // Plafond : limit > @Max(10000) → 400.
            assertThat(restTemplate.exchange(url("/api/loyalty/point-distributions?limit=999999"),
                HttpMethod.GET, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            jdbc.update("DELETE FROM loyalty_transactions WHERE account_id = ?::uuid", UUID.fromString(accId));
            jdbc.update("DELETE FROM loyalty_accounts WHERE id = ?::uuid", UUID.fromString(accId));
        }
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
