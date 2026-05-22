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
    void snap2earn_credits() {
        String admin = adminBearer();
        String[] rt = restoTenant();
        ResponseEntity<String> r = restTemplate.exchange(url("/api/loyalty/snap2earn"), HttpMethod.POST,
            jsonJwtEntity(Map.of("clientId", userId(), "restaurantId", rt[0], "amount", 150, "ticketRef", "L4-" + UUID.randomUUID().toString().substring(0, 8)), admin), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
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
}
