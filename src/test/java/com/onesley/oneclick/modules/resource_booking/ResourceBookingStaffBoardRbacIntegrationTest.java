package com.onesley.oneclick.modules.resource_booking;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC/ABAC du <b>board staff</b> {@code GET /api/resource-bookings/bookings/tenant} (W2-A —
 * dashboard temps réel des réservations de ressources, push STOMP {@code /topic/resource-bookings}).
 *
 * <p>Invariant de sécurité : l'endpoint est gardé {@code VIEW:BOOKINGS} (401 sans JWT) MAIS le
 * service exige en plus staff/admin (ABAC) — un CLIENT, pourtant porteur de {@code VIEW:BOOKINGS}
 * (il gère SES bookings), est refusé <b>403</b> ici : il n'a aucune raison de voir les réservations
 * des autres membres du tenant. L'admin (sans tenant propre) obtient une page vide (200) — le
 * scoping tenant réel est couvert par les tests unitaires {@code ResourceBookingServiceTest}.</p>
 */
class ResourceBookingStaffBoardRbacIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/api/resource-bookings/bookings/tenant";

    /** Un CLIENT réel (détient VIEW:BOOKINGS) — son sub sert de JWT. */
    private UUID clientUserId() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL ORDER BY u.id LIMIT 1", String.class));
    }

    private String clientBearer() {
        return jwtIssuer.issueAccessToken(clientUserId(), "CLIENT").token();
    }

    @Test
    void staffBoard_adminReachable_returns200PageShape() {
        ResponseEntity<String> resp = restTemplate.exchange(
            url(URL + "?page=0&size=5"), HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Forme PageResponse (le board renvoie une page paginée).
        assertThat(resp.getBody()).contains("content");
    }

    @Test
    void staffBoard_clientForbidden_returns403() {
        // CLIENT : passe @PreAuthorize (VIEW:BOOKINGS) mais le service exige staff/admin → 403.
        ResponseEntity<String> resp = restTemplate.exchange(
            url(URL), HttpMethod.GET, jwtEntity(clientBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void staffBoard_noJwt_returns401() {
        ResponseEntity<String> resp = restTemplate.exchange(
            url(URL), HttpMethod.GET, jwtEntity(null), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
