package com.onesley.oneclick.modules.loyalty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L4 « profondeur » — {@code /api/loyalty} : earn + snap2earn + comptes/transactions (lectures)
 * + gain-rule-requests + tiers. Les mutations à effets de bord lourds (spend/gift/ocr-receipt,
 * gain-rules unique-par-resto, approve/reject) sont couvertes au niveau L3 service.
 */
class LoyaltyFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String[] restoTenant() {
        return jdbc.queryForObject(
            "SELECT r.id::text || ',' || r.tenant_id::text FROM restaurants r WHERE r.tenant_id IS NOT NULL AND r.deleted_at IS NULL LIMIT 1",
            String.class).split(",");
    }
    private String userId() { return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class); }

    @Test
    void earn_thenAccountsAndTransactions() throws Exception {
        String admin = adminBearer();
        String[] rt = restoTenant();
        String restaurantId = rt[0], tenantId = rt[1], clientId = userId();

        // EARN → crée/crédite un compte
        ResponseEntity<String> earn = restTemplate.exchange(url("/api/loyalty/earn"), HttpMethod.POST,
            jsonJwtEntity(Map.of("clientId", clientId, "restaurantId", restaurantId, "points", 50, "amount", 100, "reason", "L4 earn"), admin), String.class);
        assertThat(earn.getStatusCode().is2xxSuccessful()).isTrue();

        // accounts by-client → récupère un accountId
        ResponseEntity<String> byClient = restTemplate.exchange(url("/api/loyalty/accounts/by-client/" + clientId), HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(byClient.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode accounts = om.readTree(byClient.getBody());
        assertThat(accounts.isArray()).isTrue();
        String accountId = accounts.get(0).get("id").asText();

        assertThat(restTemplate.exchange(url("/api/loyalty/accounts/" + accountId), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/loyalty/accounts/" + accountId + "/transactions"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        // GET /accounts = find-or-create (clientId + restaurantId requis), pas une liste
        assertThat(restTemplate.exchange(url("/api/loyalty/accounts?clientId=" + clientId + "&restaurantId=" + restaurantId), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/loyalty/accounts/by-restaurant/" + restaurantId), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // transactions + expired-points + tiers
        assertThat(restTemplate.exchange(url("/api/loyalty/transactions/by-client/" + clientId + "?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/loyalty/transactions/by-restaurant/" + restaurantId + "?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/loyalty/expired-points/by-client/" + clientId), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/loyalty/tiers"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/loyalty/tiers/by-tenant/" + tenantId), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void tiers_patch_updatesFields() throws Exception {
        String admin = adminBearer();
        var tiers = om.readTree(restTemplate.exchange(url("/api/loyalty/tiers"), HttpMethod.GET, jwtEntity(admin), String.class).getBody());
        assertThat(tiers.size()).as("au moins un palier seedé").isGreaterThan(0);
        String tierId = tiers.get(0).get("id").asText();
        ResponseEntity<String> patch = restTemplate.exchange(url("/api/loyalty/tiers/" + tierId), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("bonusPercent", 7.5, "minPoints", 1234), admin), String.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
        var node = om.readTree(patch.getBody());
        assertThat(node.get("minPoints").asInt()).isEqualTo(1234);
        assertThat(node.get("bonusPercent").asDouble()).isEqualTo(7.5);
    }

    @Test
    void snap2earn_credits() {
        String admin = adminBearer();
        String[] rt = restoTenant();
        ResponseEntity<String> r = restTemplate.exchange(url("/api/loyalty/snap2earn"), HttpMethod.POST,
            jsonJwtEntity(Map.of("clientId", userId(), "restaurantId", rt[0], "amount", 150, "ticketRef", "L4-" + UUID.randomUUID().toString().substring(0, 8)), admin), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
    }

    /** Solde courant d'un compte (client × restaurant) via find-or-create. */
    private int balanceOf(String bearer, String clientId, String restaurantId) throws Exception {
        ResponseEntity<String> r = restTemplate.exchange(
            url("/api/loyalty/accounts?clientId=" + clientId + "&restaurantId=" + restaurantId),
            HttpMethod.GET, jwtEntity(bearer), String.class);
        return om.readTree(r.getBody()).get("balance").asInt();
    }

    @Test
    void snap2earn_withRedeemPoints_debitsBalance() throws Exception {
        String admin = adminBearer();
        String[] rt = restoTenant();
        String restaurantId = rt[0], clientId = userId();

        // 1. Finance le compte (solde suffisant pour la conversion)
        restTemplate.exchange(url("/api/loyalty/earn"), HttpMethod.POST,
            jsonJwtEntity(Map.of("clientId", clientId, "restaurantId", restaurantId,
                "points", 100, "amount", 200, "reason", "L4 redeem-fund"), admin), String.class);
        int before = balanceOf(admin, clientId, restaurantId);

        // 2. snap2earn avec conversion (redeemPoints=20), montant faible
        ResponseEntity<String> res = restTemplate.exchange(url("/api/loyalty/snap2earn"), HttpMethod.POST,
            jsonJwtEntity(Map.of("clientId", clientId, "restaurantId", restaurantId,
                "amount", 10, "redeemPoints", 20), admin), String.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode body = om.readTree(res.getBody());
        int earned = body.get("pointsEarned").asInt();
        assertThat(body.get("pointsRedeemed").asInt()).isEqualTo(20);

        // 3. Solde après = avant + gagné - 20 (crédit puis conversion, même tx)
        assertThat(balanceOf(admin, clientId, restaurantId)).isEqualTo(before + earned - 20);
    }

    @Test
    void snap2earn_redeemExceedsBalance_returns400() {
        String[] rt = restoTenant();
        assertThat(restTemplate.exchange(url("/api/loyalty/snap2earn"), HttpMethod.POST,
            jsonJwtEntity(Map.of("clientId", userId(), "restaurantId", rt[0],
                "amount", 10, "redeemPoints", 99_999_999), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void gainRules_read_andRequests() throws Exception {
        String admin = adminBearer();
        String[] rt = restoTenant();
        assertThat(restTemplate.exchange(url("/api/loyalty/gain-rules/by-restaurant/" + rt[0]), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // gain-rule-requests : create + list + get
        ResponseEntity<String> post = restTemplate.exchange(url("/api/loyalty/gain-rule-requests"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", rt[0], "name", "Règle L4", "conversionRate", 0.1), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/loyalty/gain-rule-requests"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/loyalty/gain-rule-requests/by-restaurant/" + rt[0]), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/loyalty/gain-rule-requests/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbc.update("DELETE FROM gain_rule_requests WHERE id = ?::uuid", UUID.fromString(id)); // self-clean
    }

    @Test
    void account_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/loyalty/accounts/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void earn_invalidBody_400() {
        assertThat(restTemplate.exchange(url("/api/loyalty/earn"), HttpMethod.POST,
            jsonJwtEntity(Map.of("reason", "sans FK ni points"), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void accounts_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/loyalty/accounts?page=0&size=5"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── Mutations jusqu'ici non couvertes au L4 (spend/gift/gain-rules/approve/reject) ───

    /** Restaurant sans gain rule active (la contrainte UNIQUE par resto interdit un doublon). */
    private String restaurantWithoutGainRule() {
        return jdbc.queryForObject(
            "SELECT r.id::text FROM restaurants r WHERE r.deleted_at IS NULL " +
            "AND NOT EXISTS (SELECT 1 FROM gain_rules g WHERE g.restaurant_id = r.id AND g.deleted_at IS NULL) LIMIT 1",
            String.class);
    }

    @Test
    void spend_afterEarn() {
        String admin = adminBearer();
        String[] rt = restoTenant();
        String restaurantId = rt[0], clientId = userId();
        restTemplate.exchange(url("/api/loyalty/earn"), HttpMethod.POST,
            jsonJwtEntity(Map.of("clientId", clientId, "restaurantId", restaurantId, "points", 30, "amount", 60, "reason", "L4 earn-for-spend"), admin), String.class);
        assertThat(restTemplate.exchange(url("/api/loyalty/spend"), HttpMethod.POST,
            jsonJwtEntity(Map.of("clientId", clientId, "restaurantId", restaurantId, "points", 10, "reason", "L4 spend"), admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void gift_debitsSenderCreditsReceiver() {
        String admin = adminBearer();
        String restaurantId = restoTenant()[0];
        String receiverId = jdbc.queryForObject(
            "SELECT id::text FROM users WHERE deleted_at IS NULL AND id <> ?::uuid ORDER BY id LIMIT 1", String.class, SEED_SUPERADMIN_ID);
        // finance le sender (= admin courant = SEED_SUPERADMIN_ID) avant le don
        restTemplate.exchange(url("/api/loyalty/earn"), HttpMethod.POST,
            jsonJwtEntity(Map.of("clientId", SEED_SUPERADMIN_ID, "restaurantId", restaurantId, "points", 20, "amount", 40, "reason", "L4 gift-fund"), admin), String.class);
        assertThat(restTemplate.exchange(url("/api/loyalty/gift"), HttpMethod.POST,
            jsonJwtEntity(Map.of("receiverId", receiverId, "restaurantId", restaurantId, "points", 10, "message", "L4 gift"), admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void gainRule_crud() throws Exception {
        String admin = adminBearer();
        String resto = restaurantWithoutGainRule();
        ResponseEntity<String> create = restTemplate.exchange(url("/api/loyalty/gain-rules"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", resto, "conversionRate", 0.1), admin), String.class);
        assertThat(create.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(create.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/loyalty/gain-rules/" + id), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("conversionRate", 0.15, "isActive", true), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/loyalty/gain-rules/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();
        jdbc.update("DELETE FROM gain_rules WHERE id = ?::uuid", UUID.fromString(id)); // hard-clean après soft-delete
    }

    @Test
    void gainRuleRequest_reject() throws Exception {
        String admin = adminBearer();
        ResponseEntity<String> req = restTemplate.exchange(url("/api/loyalty/gain-rule-requests"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restoTenant()[0], "name", "Règle L4 rejet", "conversionRate", 0.1), admin), String.class);
        String id = om.readTree(req.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/loyalty/gain-rule-requests/" + id + "/reject"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("rejectionReason", "Motif L4"), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbc.update("DELETE FROM gain_rule_requests WHERE id = ?::uuid", UUID.fromString(id)); // self-clean
    }

    @Test
    void gainRuleRequest_approve_createsRule() throws Exception {
        String admin = adminBearer();
        String resto = restaurantWithoutGainRule();
        ResponseEntity<String> req = restTemplate.exchange(url("/api/loyalty/gain-rule-requests"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", resto, "name", "Règle L4 appro", "conversionRate", 0.1), admin), String.class);
        String id = om.readTree(req.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/loyalty/gain-rule-requests/" + id + "/approve"), HttpMethod.PATCH, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        // self-clean : la demande (FK created_rule_id) puis la gain rule créée
        jdbc.update("DELETE FROM gain_rule_requests WHERE id = ?::uuid", UUID.fromString(id));
        jdbc.update("DELETE FROM gain_rules WHERE restaurant_id = ?::uuid", UUID.fromString(resto));
    }

    @Test
    void ocrReceipt_stub_200() {
        // OCR_SPACE_API_KEY absente en env de test → ocr() renvoie le stub → 200 (couvre l'endpoint)
        assertThat(restTemplate.exchange(url("/api/loyalty/ocr-receipt"), HttpMethod.POST,
            jsonJwtEntity(Map.of("imageUrl", "https://example.com/ticket-l4.png"), adminBearer()), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();
    }
}
