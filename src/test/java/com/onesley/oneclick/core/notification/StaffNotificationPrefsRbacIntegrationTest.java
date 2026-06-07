package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration Gap #5 — préférences de notifications staff (self-service).
 *
 * <p>Contrat testé :
 * <ul>
 *   <li>GET sans JWT → 401 (endpoint protégé).</li>
 *   <li>GET en CLIENT (self-service) → 200 + défauts tous activés tant que rien n'a été persisté.</li>
 *   <li>PUT en CLIENT → 200 + round-trip : le GET suivant reflète les toggles persistés.</li>
 * </ul>
 *
 * <p>Tous les rôles détiennent VIEW/UPDATE:NOTIFICATIONS (V34) ; le scope est intrinsèque
 * (currentUserId()) — pas de matrice 403 par rôle ici (préférence personnelle, pas ressource admin).
 */
class StaffNotificationPrefsRbacIntegrationTest extends AbstractIntegrationTest {

    @AfterEach
    void cleanup() {
        // Supprime la row éventuellement créée pour le user CLIENT seedé (idempotence du test).
        jdbc.update(
            "DELETE FROM staff_notification_preferences WHERE user_id IN "
            + "(SELECT u.id FROM users u JOIN roles r ON r.id = u.role_id WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL)");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getPreferences_noJwt_returns401() {
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/notifications/preferences/me"), HttpMethod.GET, jwtEntity(null), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getPreferences_client_returnsAllEnabledDefaults() {
        ResponseEntity<Map> resp = restTemplate.exchange(
            url("/api/notifications/preferences/me"), HttpMethod.GET,
            jwtEntity(bearerForRole("CLIENT")), Map.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = resp.getBody();
        assertThat(body.get("booking")).isEqualTo(true);
        assertThat(body.get("reservation")).isEqualTo(true);
        assertThat(body.get("feedback")).isEqualTo(true);
        assertThat(body.get("loyalty")).isEqualTo(true);
        assertThat(body.get("system")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updateThenGet_client_persistsToggles() {
        String bearer = bearerForRole("CLIENT");
        Map<String, Object> payload = Map.of(
            "booking", false, "reservation", true, "feedback", false, "loyalty", true, "system", false);

        ResponseEntity<Map> put = restTemplate.exchange(
            url("/api/notifications/preferences/me"), HttpMethod.PUT,
            jsonJwtEntity(payload, bearer), Map.class);
        assertThat(put.getStatusCode().value()).isEqualTo(200);
        assertThat(put.getBody().get("booking")).isEqualTo(false);

        // Round-trip : le GET reflète les toggles persistés en DB.
        ResponseEntity<Map> get = restTemplate.exchange(
            url("/api/notifications/preferences/me"), HttpMethod.GET,
            jwtEntity(bearer), Map.class);
        assertThat(get.getStatusCode().value()).isEqualTo(200);
        assertThat(get.getBody().get("booking")).isEqualTo(false);
        assertThat(get.getBody().get("feedback")).isEqualTo(false);
        assertThat(get.getBody().get("system")).isEqualTo(false);
        assertThat(get.getBody().get("reservation")).isEqualTo(true);
        assertThat(get.getBody().get("loyalty")).isEqualTo(true);
    }
}
