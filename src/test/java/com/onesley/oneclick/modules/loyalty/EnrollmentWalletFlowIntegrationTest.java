package com.onesley.oneclick.modules.loyalty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — enrollments + wallet-pass (lectures déterministes + garde auth). */
class EnrollmentWalletFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private String restaurantId() { return jdbc.queryForObject("SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class); }

    @Test
    void enrollments_byRestaurant_200() {
        assertThat(restTemplate.exchange(url("/api/loyalty/enrollments/by-restaurant/" + restaurantId()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void walletPass_metadata_200() {
        assertThat(restTemplate.exchange(url("/api/loyalty/wallet-pass/metadata"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * P2.1 — E2E du chemin {@code WalletPassService.getMetadata → UserDirectoryApi}.
     * Un admin cible un user réel via {@code ?userId=} ; le nom renvoyé doit être
     * celui résolu par le contrat annuaire identity (plus de SQL natif sur users).
     */
    @Test
    void walletPass_metadata_resolvesNameViaDirectory() throws Exception {
        Map<String, Object> u = jdbc.queryForMap(
            "SELECT id::text AS id, first_name AS fn FROM users "
            + "WHERE first_name IS NOT NULL AND first_name <> '' AND deleted_at IS NULL LIMIT 1");
        String uid = (String) u.get("id");
        String firstName = (String) u.get("fn");

        ResponseEntity<String> res = restTemplate.exchange(
            url("/api/loyalty/wallet-pass/metadata?userId=" + uid),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = om.readTree(res.getBody());
        assertThat(body.get("userId").asText()).isEqualTo(uid);
        assertThat(body.get("firstName").asText()).isEqualTo(firstName);
    }

    @Test
    void enrollments_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/loyalty/enrollments/by-restaurant/" + restaurantId()),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void walletPass_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/loyalty/wallet-pass/metadata"),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
