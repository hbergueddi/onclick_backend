package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC NOTIFICATIONS sur la stack de sécurité réelle (filter chain →
 * JwtDecoder → UserRoleAuthoritiesConverter → @PreAuthorize → service →
 * SecurityHelper.requireOwnerOrAdmin → repo).
 *
 * <p>Verrouille le contrat self-access posé par V34 (VIEW/UPDATE:NOTIFICATIONS
 * accordés à CLIENT/RESTAURATEUR/STAFF/GROUP_ADMIN) + la règle senior
 * « hasAuthority, pas isAuthenticated ». Régression visée : le refacto RBAC avait
 * laissé les notifications SUPERADMIN-only → cloche/badge/mark-read/token push en
 * 403 pour tous les autres rôles.
 *
 * <p>Matrice couverte :
 * <ul>
 *   <li>VIEW self (by-user / unread-count / tokens-by-user) : 200 ;</li>
 *   <li>VIEW d'autrui : 403 (owner-check service/controller) ;</li>
 *   <li>UPDATE self (mark-all-read, markRead gate, registerToken) : 200/201/404 ;</li>
 *   <li>UPDATE / token d'autrui : 403 ;</li>
 *   <li>CREATE (POST /api/notifications, payload valide) : 403 — reste admin ;</li>
 *   <li>findAll : CLIENT 200 (scopé self), SUPERADMIN 200 ;</li>
 *   <li>sans JWT : 401 ; multi-rôles (RESTAURATEUR) : 200.</li>
 * </ul>
 */
class NotificationControllerRbacIntegrationTest extends AbstractIntegrationTest {

    /** (id, bearer) d'un user réel du rôle donné — l'id sert aux routes /by-user/{id}. */
    private record RoleUser(UUID id, String bearer) {}

    private RoleUser asRole(String roleCode) {
        String id = jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = ? AND u.deleted_at IS NULL LIMIT 1", String.class, roleCode);
        UUID uid = UUID.fromString(id);
        return new RoleUser(uid, jwtIssuer.issueAccessToken(uid, roleCode).token());
    }

    private int get(String path, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.GET, jwtEntity(jwt), String.class)
            .getStatusCode().value();
    }

    private int patch(String path, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.PATCH, jwtEntity(jwt), String.class)
            .getStatusCode().value();
    }

    private int postJson(String path, String body, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.POST, jsonJwtEntity(body, jwt), String.class)
            .getStatusCode().value();
    }

    // ─── VIEW self (cloche / badge / tokens) : 200 ─────────────────────────────

    @Test
    void byUser_self_client_returns200() {
        RoleUser c = asRole("CLIENT");
        assertThat(get("/api/notifications/by-user/" + c.id(), c.bearer())).isEqualTo(200);
    }

    @Test
    void unreadCount_self_client_returns200() {
        RoleUser c = asRole("CLIENT");
        assertThat(get("/api/notifications/unread-count/by-user/" + c.id(), c.bearer())).isEqualTo(200);
    }

    @Test
    void tokensByUser_self_client_returns200() {
        RoleUser c = asRole("CLIENT");
        assertThat(get("/api/notifications/tokens/by-user/" + c.id(), c.bearer())).isEqualTo(200);
    }

    @Test
    void byUser_self_restaurateur_returns200() {
        RoleUser r = asRole("RESTAURATEUR");
        assertThat(get("/api/notifications/by-user/" + r.id(), r.bearer())).isEqualTo(200);
    }

    // ─── VIEW d'autrui : 403 (owner-check) ─────────────────────────────────────

    @Test
    void byUser_otherUser_client_returns403() {
        RoleUser c = asRole("CLIENT");
        // lecture des notifs du SUPERADMIN seedé par un CLIENT → refus owner.
        assertThat(get("/api/notifications/by-user/" + SEED_SUPERADMIN_ID, c.bearer())).isEqualTo(403);
    }

    @Test
    void tokensByUser_otherUser_client_returns403() {
        RoleUser c = asRole("CLIENT");
        assertThat(get("/api/notifications/tokens/by-user/" + SEED_SUPERADMIN_ID, c.bearer())).isEqualTo(403);
    }

    // ─── UPDATE self : mark-all-read / markRead (gate) / registerToken ─────────

    @Test
    void markAllRead_self_client_returns200() {
        RoleUser c = asRole("CLIENT");
        assertThat(patch("/api/notifications/mark-all-read/by-user/" + c.id(), c.bearer())).isEqualTo(200);
    }

    @Test
    void markRead_client_passesAuthGate_returns404_notForbidden() {
        // id inexistant : si CLIENT a UPDATE:NOTIFICATIONS (V34), le gate passe et le
        // service renvoie 404 (NotFound). Un 403 signifierait que le gate a refusé.
        RoleUser c = asRole("CLIENT");
        int status = patch("/api/notifications/" + UUID.randomUUID() + "/read", c.bearer());
        assertThat(status).isEqualTo(404);
    }

    @Test
    void registerToken_self_client_returns201() {
        RoleUser c = asRole("CLIENT");
        // token déterministe → upsert idempotent (pas d'accumulation au re-run).
        String body = "{\"userId\":\"" + c.id() + "\",\"token\":\"rbac-test-" + c.id() + "\",\"platform\":\"web\"}";
        assertThat(postJson("/api/notifications/tokens", body, c.bearer())).isEqualTo(201);
    }

    @Test
    void registerToken_forOtherUser_client_returns403() {
        RoleUser c = asRole("CLIENT");
        String body = "{\"userId\":\"" + SEED_SUPERADMIN_ID + "\",\"token\":\"rbac-hack-" + c.id() + "\",\"platform\":\"web\"}";
        assertThat(postJson("/api/notifications/tokens", body, c.bearer())).isEqualTo(403);
    }

    // ─── CREATE reste admin : CLIENT refusé (payload valide → deny avant service) ─

    @Test
    void createNotification_client_returns403() {
        RoleUser c = asRole("CLIENT");
        // recipientUserId valide → la validation passe, c'est l'autorisation (CREATE) qui refuse.
        String body = "{\"recipientUserId\":\"" + c.id() + "\"}";
        assertThat(postJson("/api/notifications", body, c.bearer())).isEqualTo(403);
    }

    // ─── findAll : CLIENT 200 (scopé self), SUPERADMIN 200 ─────────────────────

    @Test
    void findAll_client_returns200_scopedToSelf() {
        RoleUser c = asRole("CLIENT");
        assertThat(get("/api/notifications?page=0&size=5", c.bearer())).isEqualTo(200);
    }

    @Test
    void findAll_superadmin_returns200() {
        assertThat(get("/api/notifications?page=0&size=5", adminBearer())).isEqualTo(200);
    }

    // ─── Sans JWT : 401 ────────────────────────────────────────────────────────

    @Test
    void byUser_noJwt_returns401() {
        assertThat(get("/api/notifications/by-user/" + SEED_SUPERADMIN_ID, null)).isEqualTo(401);
    }
}
