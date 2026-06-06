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
 * Intégration RBAC + ABAC des 2 audits de listing loyalty (Forge) sur la stack de
 * sécurité réelle (filter chain → JwtDecoder → @PreAuthorize → SecurityHelper →
 * service → repo → DB) :
 * <ul>
 *   <li>{@code GET /api/loyalty/redemptions} — audit paginé des rédemptions ;</li>
 *   <li>{@code GET /api/loyalty/restitutions} — audit paginé des restitutions resto.</li>
 * </ul>
 *
 * <p>Contrat vérouillé :
 * <ul>
 *   <li>Anonyme → 401 (filtre sécurité) ;</li>
 *   <li>Admin (SUPERADMIN) → 200, shape {@code PageResponse} ({@code content/totalElements}),
 *       noms client + restaurant résolus serveur-side ;</li>
 *   <li>Owner (RESTAURATEUR) → 200 sur SON restaurant, 403 sur un restaurant étranger
 *       (ABAC par-id au contrôleur) ;</li>
 *   <li>CLIENT (détient pourtant {@code VIEW:LOYALTY}) → 200 mais {@code content} vide
 *       (ABAC service : staff d'aucun resto ⇒ rien à lire) — anti-fuite cross-client.</li>
 * </ul>
 */
class LoyaltyAuditListingRbacIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private int status(String path, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.GET, jwtEntity(jwt), String.class)
            .getStatusCode().value();
    }

    /** (ownerId, ownedRestaurantId) — un RESTAURATEUR staff actif d'un restaurant. */
    private Map<String, Object> ownerPair() {
        return jdbc.queryForMap(
            "SELECT u.id::text AS uid, rs.restaurant_id::text AS rid "
            + "FROM users u JOIN roles r ON r.id = u.role_id "
            + "JOIN restaurant_staffs rs ON rs.user_id = u.id "
            + "WHERE r.code = 'RESTAURATEUR' AND rs.deleted_at IS NULL AND u.deleted_at IS NULL "
            + "ORDER BY u.id LIMIT 1");
    }

    private String foreignRestaurant(String ownerId) {
        return jdbc.queryForObject(
            "SELECT id::text FROM restaurants WHERE deleted_at IS NULL "
            + "AND id NOT IN (SELECT restaurant_id FROM restaurant_staffs WHERE user_id = ?::uuid AND deleted_at IS NULL) "
            + "LIMIT 1", String.class, ownerId);
    }

    // ─── 401 anonyme ──────────────────────────────────────────────────────────

    @Test
    void redemptions_noBearer_401() {
        assertThat(status("/api/loyalty/redemptions", null)).isEqualTo(401);
    }

    @Test
    void restitutions_noBearer_401() {
        assertThat(status("/api/loyalty/restitutions", null)).isEqualTo(401);
    }

    // ─── 200 admin + shape PageResponse ───────────────────────────────────────

    @Test
    void redemptions_admin_200_pageResponseShape() throws Exception {
        ResponseEntity<String> res = restTemplate.exchange(
            url("/api/loyalty/redemptions?page=0&size=5"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = om.readTree(res.getBody());
        assertThat(body.has("content")).isTrue();
        assertThat(body.get("content").isArray()).isTrue();
        assertThat(body.has("totalElements")).isTrue();
        assertThat(body.has("page")).isTrue();
        assertThat(body.has("size")).isTrue();
    }

    @Test
    void restitutions_admin_200_pageResponseShape() throws Exception {
        ResponseEntity<String> res = restTemplate.exchange(
            url("/api/loyalty/restitutions?page=0&size=5"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = om.readTree(res.getBody());
        assertThat(body.has("content")).isTrue();
        assertThat(body.get("content").isArray()).isTrue();
        assertThat(body.has("totalElements")).isTrue();
    }

    // ─── Flow listing : la ligne semée remonte avec ses noms résolus ──────────

    @Test
    void redemptions_admin_listsSeededRow_withResolvedNames() throws Exception {
        // Compte (client réel CLIENT, resto réel) + 1 rédemption connue.
        // Réutilise un loyalty_account EXISTANT (client CLIENT avec noms + resto réel).
        // loyalty_accounts porte une contrainte UNIQUE(client_id, restaurant_id) : ré-insérer
        // un compte pour la même paire violait la contrainte (DuplicateKey). On ne sème que la rédemption.
        String[] acr = jdbc.queryForObject(
            "SELECT la.id::text || ',' || la.client_id::text || ',' || la.restaurant_id::text "
            + "FROM loyalty_accounts la JOIN users u ON u.id = la.client_id "
            + "JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL "
            + "AND u.first_name IS NOT NULL AND u.last_name IS NOT NULL LIMIT 1",
            String.class).split(",");
        String accId = acr[0], clientId = acr[1], restaurantId = acr[2];
        String redId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO redemptions (id, account_id, points_used, discount_amount, otp_validated) "
            + "VALUES (?::uuid, ?::uuid, 120, 24.50, true)", redId, accId);
        try {
            ResponseEntity<String> res = restTemplate.exchange(
                url("/api/loyalty/redemptions?restaurantId=" + restaurantId + "&size=200"),
                HttpMethod.GET, jwtEntity(adminBearer()), String.class);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode content = om.readTree(res.getBody()).get("content");
            JsonNode seeded = null;
            for (JsonNode n : content) if (redId.equals(n.get("id").asText())) seeded = n;
            assertThat(seeded).as("la rédemption semée remonte dans l'audit").isNotNull();
            assertThat(seeded.get("clientId").asText()).isEqualTo(clientId);
            assertThat(seeded.get("restaurantId").asText()).isEqualTo(restaurantId);
            assertThat(seeded.get("pointsUsed").asInt()).isEqualTo(120);
            assertThat(seeded.get("otpValidated").asBoolean()).isTrue();
            // Noms résolus serveur-side (client via UserDirectoryApi, resto via read-view).
            assertThat(seeded.get("clientName").isNull()).isFalse();
            assertThat(seeded.get("restaurantName").isNull()).isFalse();
        } finally {
            jdbc.update("DELETE FROM redemptions WHERE id = ?::uuid", UUID.fromString(redId));
            // compte réutilisé (pré-existant) → pas de suppression du loyalty_account
        }
    }

    @Test
    void redemptions_admin_otpStatusFilter_excludesNonOtpRows() throws Exception {
        // Réutilise un loyalty_account EXISTANT (UNIQUE(client_id,restaurant_id)) → cr[0]=accountId, cr[1]=restaurantId.
        String[] cr = jdbc.queryForObject(
            "SELECT la.id::text || ',' || la.restaurant_id::text FROM loyalty_accounts la LIMIT 1",
            String.class).split(",");
        String accId = cr[0];
        String otpId = UUID.randomUUID().toString();
        String stdId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO redemptions (id, account_id, points_used, discount_amount, otp_validated) VALUES (?::uuid, ?::uuid, 10, 1.00, true)", otpId, accId);
        jdbc.update("INSERT INTO redemptions (id, account_id, points_used, discount_amount, otp_validated) VALUES (?::uuid, ?::uuid, 10, 1.00, false)", stdId, accId);
        try {
            JsonNode content = om.readTree(restTemplate.exchange(
                url("/api/loyalty/redemptions?restaurantId=" + cr[1] + "&status=otp_validated&size=200"),
                HttpMethod.GET, jwtEntity(adminBearer()), String.class).getBody()).get("content");
            boolean hasOtp = false, hasStd = false;
            for (JsonNode n : content) {
                if (otpId.equals(n.get("id").asText())) hasOtp = true;
                if (stdId.equals(n.get("id").asText())) hasStd = true;
            }
            assertThat(hasOtp).as("la rédemption OTP est incluse").isTrue();
            assertThat(hasStd).as("la rédemption standard est exclue par status=otp_validated").isFalse();
        } finally {
            jdbc.update("DELETE FROM redemptions WHERE id IN (?::uuid, ?::uuid)", UUID.fromString(otpId), UUID.fromString(stdId));
            // compte réutilisé (pré-existant) → pas de suppression du loyalty_account
        }
    }

    @Test
    void restitutions_admin_listsSeededRow_withResolvedName() throws Exception {
        String rid = jdbc.queryForObject("SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class);
        String resId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO restaurant_restitutions (id, restaurant_id, amount, points, status) "
            + "VALUES (?::uuid, ?::uuid, 333.00, 666, 'pending')", resId, rid);
        try {
            ResponseEntity<String> res = restTemplate.exchange(
                url("/api/loyalty/restitutions?restaurantId=" + rid + "&size=200"),
                HttpMethod.GET, jwtEntity(adminBearer()), String.class);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode content = om.readTree(res.getBody()).get("content");
            JsonNode seeded = null;
            for (JsonNode n : content) if (resId.equals(n.get("id").asText())) seeded = n;
            assertThat(seeded).as("la restitution semée remonte dans l'audit").isNotNull();
            assertThat(seeded.get("restaurantId").asText()).isEqualTo(rid);
            assertThat(seeded.get("points").asInt()).isEqualTo(666);
            assertThat(seeded.get("status").asText()).isEqualTo("pending");
            assertThat(seeded.get("restaurantName").isNull()).isFalse(); // résolu read-view
        } finally {
            jdbc.update("DELETE FROM restaurant_restitutions WHERE id = ?::uuid", UUID.fromString(resId));
        }
    }

    // ─── ABAC owner : 200 sur son resto, 403 sur un resto étranger ────────────

    @Test
    void redemptions_owner_ownRestaurant200_foreign403() {
        Map<String, Object> p = ownerPair();
        String ownerId = (String) p.get("uid");
        String ownedRid = (String) p.get("rid");
        String foreignRid = foreignRestaurant(ownerId);
        String owner = jwtIssuer.issueAccessToken(UUID.fromString(ownerId), "RESTAURATEUR").token();

        assertThat(status("/api/loyalty/redemptions?restaurantId=" + ownedRid, owner)).isEqualTo(200);
        assertThat(status("/api/loyalty/redemptions?restaurantId=" + foreignRid, owner)).isEqualTo(403);
    }

    @Test
    void restitutions_owner_ownRestaurant200_foreign403() {
        Map<String, Object> p = ownerPair();
        String ownerId = (String) p.get("uid");
        String ownedRid = (String) p.get("rid");
        String foreignRid = foreignRestaurant(ownerId);
        String owner = jwtIssuer.issueAccessToken(UUID.fromString(ownerId), "RESTAURATEUR").token();

        assertThat(status("/api/loyalty/restitutions?restaurantId=" + ownedRid, owner)).isEqualTo(200);
        assertThat(status("/api/loyalty/restitutions?restaurantId=" + foreignRid, owner)).isEqualTo(403);
    }

    // ─── CLIENT : gate VIEW:LOYALTY passe mais ABAC vide le contenu ───────────

    @Test
    void redemptions_client_200_butEmptyContent() throws Exception {
        ResponseEntity<String> res = restTemplate.exchange(url("/api/loyalty/redemptions"),
            HttpMethod.GET, jwtEntity(bearerForRole("CLIENT")), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = om.readTree(res.getBody());
        assertThat(body.get("content")).isEmpty();
        assertThat(body.get("totalElements").asLong()).isZero();
    }

    @Test
    void restitutions_client_200_butEmptyContent() throws Exception {
        ResponseEntity<String> res = restTemplate.exchange(url("/api/loyalty/restitutions"),
            HttpMethod.GET, jwtEntity(bearerForRole("CLIENT")), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = om.readTree(res.getBody());
        assertThat(body.get("content")).isEmpty();
        assertThat(body.get("totalElements").asLong()).isZero();
    }
}
