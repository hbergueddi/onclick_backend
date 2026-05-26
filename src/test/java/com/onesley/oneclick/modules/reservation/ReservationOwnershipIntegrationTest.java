package com.onesley.oneclick.modules.reservation;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration P2 (owner-check sweep) — anti-IDOR sur le module RESERVATIONS.
 *
 * <p>CLIENT détient {@code VIEW/CREATE/UPDATE/DELETE:RESERVATIONS} : sans scope,
 * il listait les réservations d'autrui ({@code findAll}), lisait les invités, et
 * pouvait muter le statut de n'importe quelle réservation. On verrouille au
 * contrôleur (dual-ownership) :
 * <ul>
 *   <li>{@code GET /reservations} : non-admin → forcé à SES résas, sauf staff actif
 *       du restaurant demandé (vue ProDesk) ;</li>
 *   <li>{@code PATCH /{id}/status}, invite, mark-seen : write-access (client/staff/admin,
 *       pas un simple invité) ;</li>
 *   <li>{@code guests/by-user/{userId}} : requireOwnerOrAdmin.</li>
 * </ul>
 * Anti-régression : l'admin n'est pas scopé.
 */
class ReservationOwnershipIntegrationTest extends AbstractIntegrationTest {

    private record Resa(UUID id, UUID clientId) {}

    /** Une réservation dont le client est un vrai user CLIENT. */
    private Resa clientReservation() {
        String[] p = jdbc.queryForObject(
            "SELECT res.id::text || ',' || res.client_id::text FROM reservations res "
            + "JOIN users u ON u.id = res.client_id JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND res.deleted_at IS NULL LIMIT 1", String.class).split(",");
        return new Resa(UUID.fromString(p[0]), UUID.fromString(p[1]));
    }

    private UUID otherClient(UUID excludeId) {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL AND u.id <> ?::uuid LIMIT 1",
            String.class, excludeId));
    }

    private String clientBearer(UUID id) { return jwtIssuer.issueAccessToken(id, "CLIENT").token(); }

    private ResponseEntity<String> getResp(String path, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.GET, jwtEntity(jwt), String.class);
    }

    // ─── findAll : scope SELF au niveau DONNÉES pour un non-admin ──────────────

    @Test
    void findAll_client_ignoresClientIdParam_doesNotLeakOthers() {
        Resa a = clientReservation();
        String snooper = clientBearer(otherClient(a.clientId()));
        // B demande EXPLICITEMENT les résas de A → le controller force clientId=self(B)
        ResponseEntity<String> resp = getResp(
            "/api/reservations?clientId=" + a.clientId() + "&page=0&size=50", snooper);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).doesNotContain(a.id().toString());
    }

    @Test
    void findAll_admin_seesRequestedClient() {
        Resa a = clientReservation();
        // contrôle : l'admin n'est PAS scopé → voit bien la résa de A
        ResponseEntity<String> resp = getResp(
            "/api/reservations?clientId=" + a.clientId() + "&page=0&size=50", adminBearer());
        assertThat(resp.getBody()).contains(a.id().toString());
    }

    // ─── Mutations / lectures d'autrui : 403 ───────────────────────────────────

    @Test
    void changeStatus_otherClientsReservation_returns403() {
        Resa a = clientReservation();
        String snooper = clientBearer(otherClient(a.clientId()));
        int status = restTemplate.exchange(url("/api/reservations/" + a.id() + "/status"),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("status", "confirmed"), snooper), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void guestsByUser_otherUser_returns403() {
        Resa a = clientReservation();
        String snooper = clientBearer(otherClient(a.clientId()));
        assertThat(getResp("/api/reservations/guests/by-user/" + a.clientId(), snooper)
            .getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void guestsByUser_self_returns200() {
        Resa a = clientReservation();
        assertThat(getResp("/api/reservations/guests/by-user/" + a.clientId(), clientBearer(a.clientId()))
            .getStatusCode().value()).isEqualTo(200);
    }

    // ─── guests/by-inviter (« Invitations envoyées » Pocket) : requireOwnerOrAdmin ──

    @Test
    void guestsByInviter_otherUser_returns403() {
        Resa a = clientReservation();
        String snooper = clientBearer(otherClient(a.clientId()));
        assertThat(getResp("/api/reservations/guests/by-inviter/" + a.clientId(), snooper)
            .getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void guestsByInviter_self_returns200() {
        Resa a = clientReservation();
        assertThat(getResp("/api/reservations/guests/by-inviter/" + a.clientId(), clientBearer(a.clientId()))
            .getStatusCode().value()).isEqualTo(200);
    }
}
