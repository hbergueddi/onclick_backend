package com.onesley.oneclick.modules.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ITEM 2 (intégration L4) — quotas sociaux sur la stack réelle (filter chain → JwtDecoder →
 * UserRoleAuthoritiesConverter → @PreAuthorize → service → repo → DB) :
 *
 * <ul>
 *   <li><b>Import contacts</b> : RBAC ({@code CREATE:COMMUNITY}), ABAC self (A ne peut pas
 *       importer pour B), matching by-phone/by-email, et quota (limite ramenée à 3 pour le
 *       test) → 429 au-delà.</li>
 *   <li><b>Plafond d'amis</b> : limite ramenée à 2 → la 3e demande renvoie 422 typé.</li>
 *   <li><b>Ticket friends_cap</b> : 1er → 201 ; 2e en &lt; 24 h → 409 (dédup).</li>
 * </ul>
 *
 * <p>Les seuils sont abaissés via {@code @TestPropertySource} (mergé avec la base) pour
 * rendre les bornes atteignables sans seeder 50 amitiés.
 */
@TestPropertySource(properties = {
    "app.social.friends.cap=2",
    "app.social.contact-import.daily-limit=3"
})
class SocialQuotasFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private String createClient(String admin) throws Exception {
        String roleId = jdbc.queryForObject("SELECT id::text FROM roles WHERE code='CLIENT' LIMIT 1", String.class);
        var r = restTemplate.exchange(url("/api/users"), HttpMethod.POST, jsonJwtEntity(Map.of(
            "roleId", roleId, "email", "soc-quota-" + UUID.randomUUID() + "@x.ma",
            "phone", "+2126" + (1_000_0000 + (int) (Math.random() * 8_999_9999)),
            "password", "password1234", "firstName", "Quota", "lastName", "Soc"), admin), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful())
            .as("création user jetable — reçu %s, body=%s", r.getStatusCode(), r.getBody()).isTrue();
        return om.readTree(r.getBody()).get("id").asText();
    }

    private String bearerCl(String userId) {
        return jwtIssuer.issueAccessToken(UUID.fromString(userId), "CLIENT").token();
    }

    private String phoneOf(String userId) {
        return jdbc.queryForObject("SELECT phone FROM users WHERE id = ?::uuid", String.class, UUID.fromString(userId));
    }
    private String emailOf(String userId) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id = ?::uuid", String.class, UUID.fromString(userId));
    }

    // ─── Import contacts ───────────────────────────────────────────────────────

    @Test
    void contactImport_matches_rbac_abac_andQuota() throws Exception {
        String admin = adminBearer();
        String me = createClient(admin), friend = createClient(admin), other = createClient(admin);
        String bearerMe = bearerCl(me);

        // 201/200 + matches : un client importe SON carnet (phone du friend + email de other).
        ResponseEntity<String> res = restTemplate.exchange(url("/api/social/contact-import"), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "userId", me,
                "phones", List.of(phoneOf(friend)),
                "emails", List.of(emailOf(other))), bearerMe), String.class);
        assertThat(res.getStatusCode())
            .as("import attendu 2xx — reçu %s, body=%s", res.getStatusCode(), res.getBody()).isEqualTo(HttpStatus.OK);
        var body = om.readTree(res.getBody());
        // friend + other résolus (2 matches), self non inclus.
        assertThat(body.get("matches")).hasSize(2);
        assertThat(body.get("dailyLimit").asInt()).isEqualTo(3);
        assertThat(body.get("dailyCount").asInt()).isEqualTo(1);
        assertThat(body.toString()).doesNotContain(me); // self jamais proposé

        // 403 : A (bearerMe) ne peut PAS importer pour B (userId = friend) — ABAC self-scope.
        assertThat(restTemplate.exchange(url("/api/social/contact-import"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userId", friend, "phones", List.of(), "emails", List.of()), bearerMe), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 401 : pas de JWT.
        assertThat(restTemplate.exchange(url("/api/social/contact-import"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userId", me, "phones", List.of(), "emails", List.of()), null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Quota : limite = 3. On a déjà consommé 1 → 2 imports de plus OK, le 4e → 429.
        for (int i = 0; i < 2; i++) {
            assertThat(restTemplate.exchange(url("/api/social/contact-import"), HttpMethod.POST,
                jsonJwtEntity(Map.of("userId", me, "phones", List.of(), "emails", List.of()), bearerMe), String.class)
                .getStatusCode().is2xxSuccessful()).as("import #%s sous quota", i + 2).isTrue();
        }
        assertThat(restTemplate.exchange(url("/api/social/contact-import"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userId", me, "phones", List.of(), "emails", List.of()), bearerMe), String.class)
            .getStatusCode()).as("4e import → 429").isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // self-clean (contact_imports CASCADE sur DELETE user).
        for (String u : List.of(me, friend, other)) {
            restTemplate.exchange(url("/api/users/" + u), HttpMethod.DELETE, jwtEntity(admin), String.class);
        }
    }

    @Test
    void contactImport_clientHasAuthority_butNonCommunityRoleForbidden() throws Exception {
        // RESTAURATEUR ne détient pas CREATE:COMMUNITY → 403 (verrouille la matrice RBAC).
        assertThat(restTemplate.exchange(url("/api/social/contact-import"), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "userId", SEED_SUPERADMIN_ID.toString(), "phones", List.of(), "emails", List.of()),
                bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── Plafond d'amis (cap = 2 en test) + ticket friends_cap dédupliqué ──────

    @Test
    void friendsCap_blocksBeyondLimit_andOpensDedupedTicket() throws Exception {
        String admin = adminBearer();
        String me = createClient(admin);
        String f1 = createClient(admin), f2 = createClient(admin), f3 = createClient(admin);
        String bearerMe = bearerCl(me);

        // 2 amitiés ACCEPTÉES (cap=2) : me-f1, me-f2 (créées + acceptées par admin pour aller vite).
        String id1 = createAcceptedFriendship(admin, me, f1);
        String id2 = createAcceptedFriendship(admin, me, f2);

        // friends-count = 2 (lecture self).
        ResponseEntity<String> count = restTemplate.exchange(
            url("/api/social/friends-count/by-user/" + me), HttpMethod.GET, jwtEntity(bearerMe), String.class);
        assertThat(count.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(count.getBody()).get("count").asInt()).isEqualTo(2);

        // 3e demande (me → f3) refusée : plafond atteint → 422 typé (UnprocessableException).
        ResponseEntity<String> capped = restTemplate.exchange(url("/api/social/friendships"), HttpMethod.POST,
            jsonJwtEntity(Map.of("user1Id", me, "user2Id", f3), bearerMe), String.class);
        // 422 par code numérique (Spring Boot 4 : l'enum est UNPROCESSABLE_CONTENT, même code 422).
        assertThat(capped.getStatusCode().value())
            .as("3e ami au-delà du cap — reçu %s, body=%s", capped.getStatusCode(), capped.getBody())
            .isEqualTo(422);

        // Ticket friends_cap : 1er → 201, 2e (< 24 h) → 409 (dédup).
        ResponseEntity<String> ticket1 = restTemplate.exchange(url("/api/support/friends-cap-ticket"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userId", me), bearerMe), String.class);
        assertThat(ticket1.getStatusCode())
            .as("1er ticket friends_cap — reçu %s, body=%s", ticket1.getStatusCode(), ticket1.getBody())
            .isEqualTo(HttpStatus.CREATED);
        assertThat(om.readTree(ticket1.getBody()).get("category").asText()).isEqualTo("friends_cap");
        String ticketId = om.readTree(ticket1.getBody()).get("id").asText();

        ResponseEntity<String> ticket2 = restTemplate.exchange(url("/api/support/friends-cap-ticket"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userId", me), bearerMe), String.class);
        assertThat(ticket2.getStatusCode()).as("2e ticket friends_cap < 24h → 409 dédup").isEqualTo(HttpStatus.CONFLICT);

        // 403 : ouvrir un ticket friends_cap pour un AUTRE user (ABAC self).
        assertThat(restTemplate.exchange(url("/api/support/friends-cap-ticket"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userId", f1), bearerMe), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 401 : sans JWT.
        assertThat(restTemplate.exchange(url("/api/support/friends-cap-ticket"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userId", me), null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // self-clean : ticket + friendships + users.
        jdbc.update("DELETE FROM support_tickets WHERE id = ?::uuid", UUID.fromString(ticketId));
        jdbc.update("DELETE FROM friendships WHERE id IN (?::uuid, ?::uuid)",
            UUID.fromString(id1), UUID.fromString(id2));
        for (String u : List.of(me, f1, f2, f3)) {
            restTemplate.exchange(url("/api/users/" + u), HttpMethod.DELETE, jwtEntity(admin), String.class);
        }
    }

    /** Crée une amitié (admin) puis l'accepte → status accepted (compte pour le plafond). */
    private String createAcceptedFriendship(String admin, String a, String b) throws Exception {
        ResponseEntity<String> post = restTemplate.exchange(url("/api/social/friendships"), HttpMethod.POST,
            jsonJwtEntity(Map.of("user1Id", a, "user2Id", b), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful())
            .as("création amitié — reçu %s, body=%s", post.getStatusCode(), post.getBody()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/social/friendships/" + id + "/accept"), HttpMethod.PATCH,
            jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        return id;
    }
}
