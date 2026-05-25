package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration P2 (owner-check sweep) — anti-IDOR sur le module LOYALTY.
 *
 * <p>{@code VIEW:LOYALTY} est détenu par CLIENT : sans contrôle d'ownership, un
 * client lisait le solde / les ratings / le wallet-pass d'un AUTRE client (et tous
 * les soldes d'un resto via by-restaurant). On verrouille au niveau contrôleur
 * (le service reste pur → tests unitaires sans SecurityContext intacts) :
 * <ul>
 *   <li>{@code accounts/{id}}, {@code accounts?clientId}, {@code by-restaurant} :
 *       client-self OU staff/admin du restaurant ;</li>
 *   <li>{@code ratings/scores by-user} : self OU admin (requireOwnerOrAdmin) ;</li>
 *   <li>{@code wallet-pass?userId} : self, sauf admin.</li>
 * </ul>
 * Anti-régression : le propriétaire et l'admin conservent l'accès.
 */
class LoyaltyOwnershipIntegrationTest extends AbstractIntegrationTest {

    private record Acct(UUID accountId, UUID clientId, UUID restaurantId) {}

    /** Un compte loyalty dont le client est un vrai user CLIENT. */
    private Acct ownerAccount() {
        String[] p = jdbc.queryForObject(
            "SELECT la.id::text || ',' || la.client_id::text || ',' || la.restaurant_id::text "
            + "FROM loyalty_accounts la JOIN users u ON u.id = la.client_id "
            + "JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL LIMIT 1", String.class).split(",");
        return new Acct(UUID.fromString(p[0]), UUID.fromString(p[1]), UUID.fromString(p[2]));
    }

    /** Un autre CLIENT (le « curieux ») distinct de excludeId. */
    private UUID otherClient(UUID excludeId) {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL AND u.id <> ?::uuid LIMIT 1",
            String.class, excludeId));
    }

    private String clientBearer(UUID id) {
        return jwtIssuer.issueAccessToken(id, "CLIENT").token();
    }

    private int get(String path, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.GET, jwtEntity(jwt), String.class)
            .getStatusCode().value();
    }

    // ─── CLIENT « curieux » ne lit PAS les données d'un AUTRE individu ─────────

    @Test
    void account_byId_otherClient_returns403() {
        Acct a = ownerAccount();
        String snooper = clientBearer(otherClient(a.clientId()));
        assertThat(get("/api/loyalty/accounts/" + a.accountId(), snooper)).isEqualTo(403);
    }

    @Test
    void account_byClientParam_otherClient_returns403() {
        Acct a = ownerAccount();
        String snooper = clientBearer(otherClient(a.clientId()));
        assertThat(get("/api/loyalty/accounts?clientId=" + a.clientId()
            + "&restaurantId=" + a.restaurantId(), snooper)).isEqualTo(403);
    }

    @Test
    void accountsByRestaurant_nonStaffClient_returns403() {
        Acct a = ownerAccount();
        String snooper = clientBearer(otherClient(a.clientId()));
        assertThat(get("/api/loyalty/accounts/by-restaurant/" + a.restaurantId(), snooper)).isEqualTo(403);
    }

    @Test
    void ratingsByUser_otherClient_returns403() {
        Acct a = ownerAccount();
        String snooper = clientBearer(otherClient(a.clientId()));
        assertThat(get("/api/loyalty/ratings/by-user/" + a.clientId(), snooper)).isEqualTo(403);
    }

    @Test
    void walletPass_forOtherUser_client_returns403() {
        Acct a = ownerAccount();
        String snooper = clientBearer(otherClient(a.clientId()));
        assertThat(get("/api/loyalty/wallet-pass?userId=" + a.clientId(), snooper)).isEqualTo(403);
    }

    // ─── Le propriétaire et l'admin gardent l'accès (anti-régression) ─────────

    @Test
    void account_byId_owner_returns200() {
        Acct a = ownerAccount();
        assertThat(get("/api/loyalty/accounts/" + a.accountId(), clientBearer(a.clientId()))).isEqualTo(200);
    }

    @Test
    void account_byId_admin_returns200() {
        Acct a = ownerAccount();
        assertThat(get("/api/loyalty/accounts/" + a.accountId(), adminBearer())).isEqualTo(200);
    }

    @Test
    void accountsByRestaurant_admin_returns200() {
        Acct a = ownerAccount();
        assertThat(get("/api/loyalty/accounts/by-restaurant/" + a.restaurantId(), adminBearer())).isEqualTo(200);
    }
}
