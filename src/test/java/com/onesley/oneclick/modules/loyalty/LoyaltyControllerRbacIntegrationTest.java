package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC LOYALTY sur la stack de sécurité réelle (filter chain →
 * JwtDecoder → UserRoleAuthoritiesConverter → @PreAuthorize → SecurityHelper →
 * service → repo).
 *
 * <p>Verrouille le contrat posé par V35 + le garde-fou {@code requireManagerOrAdmin} :
 * le STAFF (« Contrôleur ») obtient {@code CREATE:LOYALTY} pour SCANNER (Snap2Earn /
 * earn / ocr-receipt / ratings honoré-no_show), MAIS la nature grossière de cette
 * authority couvre aussi la config restaurant (gain rules, restitutions) — refermée
 * au niveau contrôleur pour gérant/admin uniquement.
 *
 * <p>Note data : {@code STAFF} n'a AUCUN user seedé dans {@code oneclick_enterprise}
 * (tous les comptes resto sont {@code RESTAURATEUR}) → on crée un fixture STAFF
 * jetable par test (les autorités sont chargées depuis le rôle DB du {@code sub},
 * pas depuis le claim du JWT — cf {@code UserRoleAuthoritiesConverter}).
 *
 * <p>Matrice couverte :
 * <ul>
 *   <li>STAFF Snap2Earn (CREATE:LOYALTY V35) : gate passe → 200 ;</li>
 *   <li>STAFF transition réservation (confirm/cancel/honoré/no_show = UPDATE:RESERVATIONS) : 404 (gate passe, pas 403) ;</li>
 *   <li>STAFF createGainRule / createRestitution / createGainRuleRequest : 403 (garde-fou gérant) ;</li>
 *   <li>RESTAURATEUR createGainRule : passe le garde-fou (400 doublon, PAS 403) — anti-régression ;</li>
 *   <li>CLIENT Snap2Earn : 403 (jamais accordé) — inchangé par V35.</li>
 * </ul>
 */
class LoyaltyControllerRbacIntegrationTest extends AbstractIntegrationTest {

    private UUID staffUserId;
    private String staffBearer;

    @BeforeEach
    void createStaffFixture() {
        staffUserId = UUID.fromString(jdbc.queryForObject(
            "INSERT INTO users (role_id, email, password_hash, first_name, last_name) "
            + "VALUES ((SELECT id FROM roles WHERE code = 'STAFF'), ?, 'x', 'Rbac', 'Staff') "
            + "RETURNING id::text",
            String.class, "rbac-staff-" + UUID.randomUUID() + "@test.local"));
        staffBearer = jwtIssuer.issueAccessToken(staffUserId, "STAFF").token();
    }

    @AfterEach
    void dropStaffFixture() {
        if (staffUserId != null) jdbc.update("DELETE FROM users WHERE id = ?", staffUserId);
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private int post(String path, Map<String, Object> body, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.POST, jsonJwtEntity(body, jwt), String.class)
            .getStatusCode().value();
    }

    private int patch(String path, Map<String, Object> body, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.PATCH, jsonJwtEntity(body, jwt), String.class)
            .getStatusCode().value();
    }

    /** Bearer d'un user réel du rôle donné (les autorités viennent du rôle DB). */
    private String bearerOf(String roleCode) {
        String id = jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = ? AND u.deleted_at IS NULL LIMIT 1", String.class, roleCode);
        return jwtIssuer.issueAccessToken(UUID.fromString(id), roleCode).token();
    }

    /** (clientId, restaurantId) d'un compte loyalty EXISTANT — même ligne → pas de findOrCreate. */
    private String[] existingAccountPair() {
        return jdbc.queryForObject(
            "SELECT client_id::text || ',' || restaurant_id::text FROM loyalty_accounts LIMIT 1",
            String.class).split(",");
    }

    // ─── STAFF peut SCANNER : gate CREATE:LOYALTY passe (V35) ─────────────────

    @Test
    void snap2earn_staff_passesGate_returns200() {
        // amount 0.01 → floor(0.01 × rate≤1) = 0 point → early-return sans transaction.
        // Compte (client, resto) déjà existant → findOrCreate ne crée rien → zéro effet de bord.
        String[] cr = existingAccountPair();
        int status = post("/api/loyalty/snap2earn",
            Map.of("clientId", cr[0], "restaurantId", cr[1], "amount", 0.01), staffBearer);
        assertThat(status).isEqualTo(200);
    }

    @Test
    void reservationStatusChange_staff_passesGate_returns404_notForbidden() {
        // confirmer / annuler / contre-proposer / honoré / no_show passent TOUS par
        // PATCH /{id}/status = UPDATE:RESERVATIONS, déjà détenu par STAFF (pas de grant requis).
        // id inexistant + status valide → 404 (NotFound) ; un 403 signifierait gate refusé.
        int status = patch("/api/reservations/" + UUID.randomUUID() + "/status",
            Map.of("status", "confirmed"), staffBearer);
        assertThat(status).isEqualTo(404);
    }

    // ─── Garde-fou : STAFF ne pilote PAS la config restaurant (gérant/admin only) ──

    @Test
    void createGainRule_staff_returns403_managerOnly() {
        // @PreAuthorize('CREATE:LOYALTY') passe (V35), puis requireManagerOrAdmin referme → 403.
        // restaurantId aléatoire : le garde-fou s'exécute AVANT toute persistance.
        int status = post("/api/loyalty/gain-rules",
            Map.of("restaurantId", UUID.randomUUID().toString(), "conversionRate", 0.1), staffBearer);
        assertThat(status).isEqualTo(403);
    }

    @Test
    void createRestitution_staff_returns403_managerOnly() {
        int status = post("/api/loyalty/restitutions",
            Map.of("restaurantId", UUID.randomUUID().toString(), "amount", 10.00), staffBearer);
        assertThat(status).isEqualTo(403);
    }

    @Test
    void createGainRuleRequest_staff_returns403_managerOnly() {
        int status = post("/api/loyalty/gain-rule-requests",
            Map.of("restaurantId", UUID.randomUUID().toString(), "name", "x", "conversionRate", 0.1), staffBearer);
        assertThat(status).isEqualTo(403);
    }

    // ─── Anti-régression : le garde-fou laisse passer le GÉRANT ────────────────

    @Test
    void createGainRule_restaurateur_passesManagerFence_not403() {
        // RESTAURATEUR passe requireManagerOrAdmin → atteint le service ; resto avec règle
        // active existante → 400 (doublon UNIQUE), donc PAS 403 et zéro création.
        String restoWithRule = jdbc.queryForObject(
            "SELECT restaurant_id::text FROM gain_rules WHERE deleted_at IS NULL LIMIT 1", String.class);
        int status = post("/api/loyalty/gain-rules",
            Map.of("restaurantId", restoWithRule, "conversionRate", 0.1), bearerOf("RESTAURATEUR"));
        assertThat(status).isEqualTo(400);
    }

    // ─── Inchangé par V35 : CLIENT n'a jamais CREATE:LOYALTY ───────────────────

    @Test
    void snap2earn_client_returns403_unchanged() {
        String[] cr = existingAccountPair();
        int status = post("/api/loyalty/snap2earn",
            Map.of("clientId", cr[0], "restaurantId", cr[1], "amount", 0.01), bearerOf("CLIENT"));
        assertThat(status).isEqualTo(403);
    }
}
