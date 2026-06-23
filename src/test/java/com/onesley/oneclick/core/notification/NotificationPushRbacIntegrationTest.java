package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC des endpoints push FCM (Sprint B.8.4) sur la stack de sécurité réelle
 * (filter chain → JwtDecoder → UserRoleAuthoritiesConverter → {@code @PreAuthorize} →
 * {@code FcmPushService}).
 *
 * <p>Verrouille la règle senior « {@code hasAuthority}, pas {@code isAuthenticated}/{@code hasRole} » :
 * {@code POST /push/{promo,reservation}} exigent {@code CREATE:NOTIFICATIONS} (admin-only).
 *
 * <p>FCM n'étant pas configuré dans le profil de test ({@code app.fcm.*} vides), l'admin obtient
 * {@code 200} + message {@code "FCM not configured"} — aucun appel réseau réel (mode stub).
 *
 * <p>Matrice : CLIENT → 403 (payload valide, refus à l'autorisation) ; SUPERADMIN → 200 stub ;
 * sans JWT → 401.
 */
class NotificationPushRbacIntegrationTest extends AbstractIntegrationTest {

    private record RoleUser(UUID id, String bearer) {}

    private RoleUser asRole(String roleCode) {
        String id = jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = ? AND u.deleted_at IS NULL LIMIT 1", String.class, roleCode);
        UUID uid = UUID.fromString(id);
        return new RoleUser(uid, jwtIssuer.issueAccessToken(uid, roleCode).token());
    }

    private ResponseEntity<String> postJson(String path, String body, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.POST, jsonJwtEntity(body, jwt), String.class);
    }

    private String promoBody() {
        return "{\"userIds\":[\"" + UUID.randomUUID() + "\"],\"title\":\"Promo\",\"body\":\"-20%\"}";
    }

    private String reservationBody() {
        return "{\"reservationId\":\"" + UUID.randomUUID()
            + "\",\"recipientUserId\":\"" + SEED_SUPERADMIN_ID
            + "\",\"status\":\"confirmed\",\"title\":\"Résa\",\"body\":\"Confirmée\"}";
    }

    // ─── CLIENT : 403 (payload valide → refus à l'autorisation CREATE:NOTIFICATIONS) ──

    @Test
    void pushPromo_client_returns403() {
        RoleUser c = asRole("CLIENT");
        assertThat(postJson("/api/notifications/push/promo", promoBody(), c.bearer())
            .getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void pushReservation_client_returns403() {
        RoleUser c = asRole("CLIENT");
        assertThat(postJson("/api/notifications/push/reservation", reservationBody(), c.bearer())
            .getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void pushPromo_restaurateur_returns403() {
        RoleUser r = asRole("RESTAURATEUR");
        assertThat(postJson("/api/notifications/push/promo", promoBody(), r.bearer())
            .getStatusCode().value()).isEqualTo(403);
    }

    // ─── SUPERADMIN : 200 (gate passé) + stub (FCM non configuré en test) ──────

    @Test
    void pushPromo_admin_returns200_stubMode() {
        ResponseEntity<String> resp = postJson("/api/notifications/push/promo", promoBody(), adminBearer());
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).contains("FCM not configured");
    }

    @Test
    void pushReservation_admin_returns200_stubMode() {
        ResponseEntity<String> resp = postJson("/api/notifications/push/reservation", reservationBody(), adminBearer());
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).contains("FCM not configured");
    }

    // ─── Sans JWT : 401 ────────────────────────────────────────────────────────

    @Test
    void pushPromo_noJwt_returns401() {
        assertThat(postJson("/api/notifications/push/promo", promoBody(), null)
            .getStatusCode().value()).isEqualTo(401);
    }
}
