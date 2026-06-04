package com.onesley.oneclick.modules.reservation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V65 — visibilité STAFF des allergènes du client sur la fiche réservation.
 *
 * <p>La résa est enrichie via la read-view native ({@code LEFT JOIN users u}) ;
 * on y projette désormais {@code array_to_string(u.allergens, ',') AS clientAllergens},
 * mappé en {@code List<String>} dans {@code ReservationDto.clientAllergens}. On
 * vérifie sur la stack réelle que :
 * <ul>
 *   <li>la liste paginée {@code GET /api/reservations?restaurantId=…} expose
 *       {@code clientAllergens} pour la résa du client allergène ;</li>
 *   <li>le batch {@code GET /api/reservations/by-restaurants} l'expose aussi
 *       (2e requête native enrichie).</li>
 * </ul>
 *
 * <p>Pas de nouvelle autorité : {@code clientAllergens} voyage sur une
 * {@code ReservationDto} déjà scopée (staff/admin via l'ABAC réservation existant).
 * On lit en SUPERADMIN (accès à toutes les résas) pour cibler le seed sans
 * dépendre du staffing d'un resto précis.
 */
class ReservationClientAllergensIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    /** Restaurant + tenant seedés (résa = FK restaurant + tenant). */
    private String[] restoTenant() {
        return jdbc.queryForObject(
            "SELECT r.id::text || ',' || r.tenant_id::text FROM restaurants r "
            + "WHERE r.tenant_id IS NOT NULL AND r.deleted_at IS NULL LIMIT 1",
            String.class).split(",");
    }

    /** CLIENT jetable avec allergènes posés directement en DB (DML autorisée à l'app user). */
    private String createClientWithAllergens(String admin, String... allergens) throws Exception {
        String roleId = jdbc.queryForObject("SELECT id::text FROM roles WHERE code='CLIENT' LIMIT 1", String.class);
        var r = restTemplate.exchange(url("/api/users"), HttpMethod.POST, jsonJwtEntity(Map.of(
            "roleId", roleId, "email", "resa-allergens-" + UUID.randomUUID() + "@x.ma",
            "password", "password1234", "firstName", "Resa", "lastName", "Allergens"), admin), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("création client jetable").isTrue();
        String clientId = om.readTree(r.getBody()).get("id").asText();
        // Pose les allergènes via PATCH /me (self-service, même chemin que le legacy).
        String bearer = jwtIssuer.issueAccessToken(UUID.fromString(clientId), "CLIENT").token();
        var patch = restTemplate.exchange(url("/api/users/me"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("allergens", java.util.List.of(allergens)), bearer), String.class);
        assertThat(patch.getStatusCode()).as("PATCH /me allergens").isEqualTo(HttpStatus.OK);
        return clientId;
    }

    /** Retrouve une résa par id dans un tableau JSON ({@code content} ou batch direct). */
    private JsonNode findById(JsonNode array, String id) {
        for (JsonNode n : array) {
            if (n.get("id").asText().equals(id)) return n;
        }
        return null;
    }

    @Test
    void list_and_byRestaurants_exposeClientAllergens() throws Exception {
        String admin = adminBearer();
        String[] rt = restoTenant();
        String restaurantId = rt[0], tenantId = rt[1];
        String clientId = createClientWithAllergens(admin, "gluten", "shellfish");

        // Crée une résa pour ce client allergène (date future, validée par le trigger).
        ResponseEntity<String> post = restTemplate.exchange(url("/api/reservations"), HttpMethod.POST,
            jsonJwtEntity(Map.of("tenantId", tenantId, "clientId", clientId, "restaurantId", restaurantId,
                "reservationAt", Instant.now().plus(4, ChronoUnit.DAYS).toString(),
                "guestCount", 2, "notes", "allergens-it"), admin), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String resaId = om.readTree(post.getBody()).get("id").asText();

        // (1) Liste paginée enrichie (findAllWithJoins) — large size pour contenir la résa.
        ResponseEntity<String> list = restTemplate.exchange(
            url("/api/reservations?restaurantId=" + restaurantId + "&page=0&size=200"),
            HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode mineInList = findById(om.readTree(list.getBody()).get("content"), resaId);
        assertThat(mineInList).as("résa présente dans la liste enrichie").isNotNull();
        JsonNode allergensInList = mineInList.get("clientAllergens");
        assertThat(allergensInList).as("clientAllergens présent dans le DTO liste").isNotNull();
        assertThat(allergensInList).hasSize(2);
        assertThat(java.util.List.of(allergensInList.get(0).asText(), allergensInList.get(1).asText()))
            .containsExactlyInAnyOrder("gluten", "shellfish");

        // (2) Batch by-restaurants (findEnrichedByRestaurantIds) — 2e requête native.
        ResponseEntity<String> batch = restTemplate.exchange(
            url("/api/reservations/by-restaurants?restaurantIds=" + restaurantId + "&limit=200"),
            HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(batch.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode mineInBatch = findById(om.readTree(batch.getBody()), resaId);
        assertThat(mineInBatch).as("résa présente dans le batch by-restaurants").isNotNull();
        assertThat(mineInBatch.get("clientAllergens")).as("clientAllergens présent dans le DTO batch").hasSize(2);

        // cleanup : annule la résa + supprime le client jetable.
        restTemplate.exchange(url("/api/reservations/" + resaId + "/status"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "cancelled", "changedById", clientId, "reason", "allergens cleanup"), admin), String.class);
        restTemplate.exchange(url("/api/users/" + clientId), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }
}
