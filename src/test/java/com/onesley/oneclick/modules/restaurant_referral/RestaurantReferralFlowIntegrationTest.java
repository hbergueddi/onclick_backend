package com.onesley.oneclick.modules.restaurant_referral;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.modules.restaurant_referral.internal.RestaurantReferralDashboardPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration E2E du parrainage RESTAURANT-à-RESTAURANT (owner → owner) — V100.
 *
 * <p>Couvre la stack HTTP réelle (filter chain → JWT → {@code @PreAuthorize} → service → DB) :
 * <ul>
 *   <li><b>RBAC</b> ({@code hasAuthority('VERB:RESTAURANT_REFERRAL')}) : CLIENT → 403 ;
 *       RESTAURATEUR (grants V100) → 200/201.</li>
 *   <li><b>ABAC owner-scope</b> : un owner ne peut agir que sur SON resto (resto tiers → 403).</li>
 *   <li><b>Flux create → activate → points</b> : le PARRAIN seul est crédité (poll du listener
 *       loyalty async sur {@code loyalty_transactions}, reason {@code RESTAURANT_REFERRAL}).</li>
 *   <li><b>STOMP</b> : {@code computeSnapshot()} tourne contre le vrai schéma + topic attendu.</li>
 * </ul>
 *
 * <p>Fixtures jetables : 2 owners (RESTAURATEUR) + 2 restos dans le tenant {@code oneclick}, nettoyés
 * en {@code @AfterEach} (CASCADE supprime restaurant_referrals/staffs/loyalty).
 */
class RestaurantReferralFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    RestaurantReferralDashboardPublisher publisher;

    private UUID oneclickTenant;
    private UUID restaurateurRoleId;

    private UUID ownerA;
    private UUID ownerB;
    private UUID restoA; // parrain
    private UUID restoB; // filleul
    private String bearerA;
    private String bearerB;

    @BeforeEach
    void setup() {
        oneclickTenant = UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = 'oneclick' LIMIT 1", String.class));
        restaurateurRoleId = UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM roles WHERE code = 'RESTAURATEUR' LIMIT 1", String.class));

        ownerA = insertOwnerUser("rr-ownerA");
        ownerB = insertOwnerUser("rr-ownerB");
        restoA = insertOneClickRestaurant("RR Parrain");
        restoB = insertOneClickRestaurant("RR Filleul");
        insertOwnerStaff(restoA, ownerA);
        insertOwnerStaff(restoB, ownerB);

        bearerA = jwtIssuer.issueAccessToken(ownerA, "RESTAURATEUR").token();
        bearerB = jwtIssuer.issueAccessToken(ownerB, "RESTAURATEUR").token();
    }

    @AfterEach
    void cleanup() {
        // CASCADE depuis restaurants → restaurant_referrals / restaurant_staffs / loyalty_accounts.
        if (restoA != null) jdbc.update("DELETE FROM restaurants WHERE id = ?", restoA);
        if (restoB != null) jdbc.update("DELETE FROM restaurants WHERE id = ?", restoB);
        if (ownerA != null) jdbc.update("DELETE FROM users WHERE id = ?", ownerA);
        if (ownerB != null) jdbc.update("DELETE FROM users WHERE id = ?", ownerB);
    }

    private UUID insertOwnerUser(String prefix) {
        return UUID.fromString(jdbc.queryForObject(
            "INSERT INTO users (tenant_id, role_id, email, password_hash, first_name, last_name) "
            + "VALUES (?::uuid, ?::uuid, ?, 'x', 'RR', 'Owner') RETURNING id::text",
            String.class, oneclickTenant, restaurateurRoleId,
            prefix + "-" + UUID.randomUUID() + "@test.local"));
    }

    private UUID insertOneClickRestaurant(String name) {
        return UUID.fromString(jdbc.queryForObject(
            "INSERT INTO restaurants (tenant_id, name, city) VALUES (?::uuid, ?, 'Casablanca') RETURNING id::text",
            String.class, oneclickTenant, name));
    }

    private void insertOwnerStaff(UUID restaurantId, UUID userId) {
        jdbc.update(
            "INSERT INTO restaurant_staffs (restaurant_id, user_id, role_code) VALUES (?::uuid, ?::uuid, 'owner')",
            restaurantId, userId);
    }

    // ─── RBAC ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void myCode_client_returns403() {
        int status = restTemplate.exchange(
            url("/api/restaurant-referrals/my-code?restaurantId=" + restoA),
            HttpMethod.GET, jwtEntity(bearerForRole("CLIENT")), String.class).getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void activate_client_returns403() {
        int status = restTemplate.exchange(
            url("/api/restaurant-referrals/activate"), HttpMethod.POST,
            jsonJwtEntity(Map.of("code", "RR-XXXXXX", "refereeRestaurantId", restoB.toString()),
                bearerForRole("CLIENT")),
            String.class).getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void mine_restaurateurOwner_returns200() {
        int status = restTemplate.exchange(
            url("/api/restaurant-referrals/mine?restaurantId=" + restoA),
            HttpMethod.GET, jwtEntity(bearerA), String.class).getStatusCode().value();
        assertThat(status).isEqualTo(200);
    }

    // ─── ABAC owner-scope ───────────────────────────────────────────────────────────────────────

    @Test
    void myCode_owner_otherRestaurant_returns403_abac() {
        // ownerB n'est pas owner de restoA → 403 même s'il a l'autorité RBAC.
        int status = restTemplate.exchange(
            url("/api/restaurant-referrals/my-code?restaurantId=" + restoA),
            HttpMethod.GET, jwtEntity(bearerB), String.class).getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void activate_owner_notOwnerOfReferee_returns403_abac() {
        // ownerA tente d'activer POUR restoB (qu'il ne possède pas) → 403.
        int status = restTemplate.exchange(
            url("/api/restaurant-referrals/activate"), HttpMethod.POST,
            jsonJwtEntity(Map.of("code", "RR-XXXXXX", "refereeRestaurantId", restoB.toString()), bearerA),
            String.class).getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    // ─── Flux E2E create → activate → points ────────────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void e2e_createCode_activate_creditsReferrerOnly() {
        // 1. Owner A obtient son code (VIEW/CREATE).
        ResponseEntity<Map> codeRes = restTemplate.exchange(
            url("/api/restaurant-referrals/my-code?restaurantId=" + restoA),
            HttpMethod.GET, jwtEntity(bearerA), Map.class);
        assertThat(codeRes.getStatusCode().value()).isEqualTo(200);
        String code = (String) codeRes.getBody().get("referralCode");
        assertThat(code).startsWith("RR-");
        assertThat(codeRes.getBody().get("status")).isEqualTo("pending");

        // 2. Owner B (resto filleul) active le code (CREATE) → 201.
        ResponseEntity<Map> actRes = restTemplate.exchange(
            url("/api/restaurant-referrals/activate"), HttpMethod.POST,
            jsonJwtEntity(Map.of("code", code, "refereeRestaurantId", restoB.toString()), bearerB),
            Map.class);
        assertThat(actRes.getStatusCode().value()).isEqualTo(201);
        assertThat(actRes.getBody().get("status")).isEqualTo("activated");
        assertThat(actRes.getBody().get("refereeRestaurantId")).isEqualTo(restoB.toString());
        int reward = ((Number) actRes.getBody().get("rewardPoints")).intValue();
        assertThat(reward).isGreaterThan(0);

        // 3. Le PARRAIN (ownerA / restoA) est crédité via le listener loyalty async — reason dédiée.
        assertThat(awaitReferralCredit(ownerA, restoA, reward))
            .as("points crédités au parrain (ownerA, restoA) via listener loyalty").isTrue();

        // 4. Le FILLEUL (ownerB / restoB) ne reçoit AUCUN point de fidélité du parrainage.
        Integer refereeTx = jdbc.queryForObject(
            "SELECT count(*) FROM loyalty_transactions t JOIN loyalty_accounts a ON a.id = t.account_id "
            + "WHERE a.client_id = ? AND a.restaurant_id = ? AND t.reason = 'RESTAURANT_REFERRAL'",
            Integer.class, ownerB, restoB);
        assertThat(refereeTx).as("le filleul ne reçoit rien").isEqualTo(0);

        // 5. Double-activation refusée (resto filleul déjà parrainé) → 409.
        int dup = restTemplate.exchange(
            url("/api/restaurant-referrals/activate"), HttpMethod.POST,
            jsonJwtEntity(Map.of("code", code, "refereeRestaurantId", restoB.toString()), bearerB),
            String.class).getStatusCode().value();
        assertThat(dup).isEqualTo(409);
    }

    /**
     * Attend (bounded ~8s) qu'une transaction loyalty {@code earn}/{@code RESTAURANT_REFERRAL} du
     * montant attendu apparaisse pour le couple (parrain, resto parrain) — le listener loyalty est
     * async ({@code @ApplicationModuleListener} = after-commit + @Async).
     */
    private boolean awaitReferralCredit(UUID referrerUserId, UUID referrerRestaurantId, int points) {
        for (int i = 0; i < 40; i++) {
            Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM loyalty_transactions t JOIN loyalty_accounts a ON a.id = t.account_id "
                + "WHERE a.client_id = ? AND a.restaurant_id = ? AND t.type = 'earn' "
                + "AND t.reason = 'RESTAURANT_REFERRAL' AND t.points = ?",
                Integer.class, referrerUserId, referrerRestaurantId, points);
            if (count != null && count > 0) return true;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    // ─── STOMP ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void dashboardPublisher_pushNow_runsAgainstRealSchema_andTopicMatches() {
        // pushNow() → publishIfChanged() → computeSnapshot() : exécute la requête native
        // SELECT count(*), max(updated_at) FROM restaurant_referrals contre le VRAI schéma.
        // Ne doit pas lever (table/colonnes correctes). Le topic est la destination STOMP figée.
        publisher.pushNow();
        assertThat(RestaurantReferralDashboardPublisher.TOPIC)
            .isEqualTo("/topic/admin/restaurant-referrals");
    }
}
