package com.onesley.oneclick.modules.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/social} : friendships, referrals, favorites, friend-groups + members. */
class SocialFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private List<String> twoUsers() {
        return jdbc.queryForList("SELECT id::text FROM users WHERE deleted_at IS NULL ORDER BY id LIMIT 2", String.class);
    }
    private String restaurantId() { return jdbc.queryForObject("SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class); }

    /** User CLIENT jetable (évite de toucher/supprimer les données seed). Échoue clairement si la création rate. */
    private String createUser(String admin) throws Exception {
        String roleId = jdbc.queryForObject("SELECT id::text FROM roles WHERE code='CLIENT' LIMIT 1", String.class);
        var r = restTemplate.exchange(url("/api/users"), HttpMethod.POST, jsonJwtEntity(Map.of(
            "roleId", roleId, "email", "l4-soc-" + UUID.randomUUID() + "@x.ma",
            "password", "password1234", "firstName", "L4", "lastName", "Soc"), admin), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("création user jetable").isTrue();
        return om.readTree(r.getBody()).get("id").asText();
    }

    @Test
    void friendships_create_accept_decline() throws Exception {
        String admin = adminBearer();
        String a = createUser(admin), b = createUser(admin); // users jetables, pas de seed
        ResponseEntity<String> post = restTemplate.exchange(url("/api/social/friendships"), HttpMethod.POST,
            jsonJwtEntity(Map.of("user1Id", a, "user2Id", b), admin), String.class);
        // diagnostic : si non-2xx (flake observé sous charge pleine suite), on expose statut + corps RFC7807
        assertThat(post.getStatusCode().is2xxSuccessful())
            .as("friendship create attendu 2xx — reçu %s, body=%s", post.getStatusCode(), post.getBody()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/social/friendships/by-user/" + a), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/social/friendships/" + id + "/accept"), HttpMethod.PATCH, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/social/friendships/" + id + "/decline"), HttpMethod.PATCH, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        // self-clean : friendship des users jetables + les users
        jdbc.update("DELETE FROM friendships WHERE id = ?::uuid", java.util.UUID.fromString(id));
        restTemplate.exchange(url("/api/users/" + a), HttpMethod.DELETE, jwtEntity(admin), String.class);
        restTemplate.exchange(url("/api/users/" + b), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void deleteFriendship_hardRemovesRow_andRbac() throws Exception {
        String admin = adminBearer();
        String a = createUser(admin), b = createUser(admin), c = createUser(admin); // users jetables
        // crée l'amitié (a,b)
        ResponseEntity<String> post = restTemplate.exchange(url("/api/social/friendships"), HttpMethod.POST,
            jsonJwtEntity(Map.of("user1Id", a, "user2Id", b), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful())
            .as("friendship create attendu 2xx — reçu %s, body=%s", post.getStatusCode(), post.getBody()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();

        // 403 : un tiers CLIENT (a DELETE:COMMUNITY mais n'est PAS partie) → bloqué par l'ABAC service
        String outsider = jwtIssuer.issueAccessToken(UUID.fromString(c), "CLIENT").token();
        assertThat(restTemplate.exchange(url("/api/social/friendships/" + id), HttpMethod.DELETE, jwtEntity(outsider), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 204 : une partie (user a, CLIENT) supprime définitivement (valide aussi le grant V38 DELETE:COMMUNITY)
        String partyA = jwtIssuer.issueAccessToken(UUID.fromString(a), "CLIENT").token();
        assertThat(restTemplate.exchange(url("/api/social/friendships/" + id), HttpMethod.DELETE, jwtEntity(partyA), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // hard delete : la row n'existe plus (≠ decline qui garde la row en status=declined)
        Integer remaining = jdbc.queryForObject(
            "SELECT count(*) FROM friendships WHERE id = ?::uuid", Integer.class, UUID.fromString(id));
        assertThat(remaining).isZero();

        // 404 : id inexistant
        assertThat(restTemplate.exchange(url("/api/social/friendships/" + UUID.randomUUID()), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // self-clean users jetables
        restTemplate.exchange(url("/api/users/" + a), HttpMethod.DELETE, jwtEntity(admin), String.class);
        restTemplate.exchange(url("/api/users/" + b), HttpMethod.DELETE, jwtEntity(admin), String.class);
        restTemplate.exchange(url("/api/users/" + c), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void pendingReceived_byUser_returnsReceivedRequests_andRbac() throws Exception {
        String admin = adminBearer();
        String a = createUser(admin), b = createUser(admin); // a = demandeur, b = destinataire
        String bearerA = jwtIssuer.issueAccessToken(UUID.fromString(a), "CLIENT").token();
        String bearerB = jwtIssuer.issueAccessToken(UUID.fromString(b), "CLIENT").token();

        // a DEMANDE b (avec le bearer de a → requested_by = a, status pending)
        ResponseEntity<String> post = restTemplate.exchange(url("/api/social/friendships"), HttpMethod.POST,
            jsonJwtEntity(Map.of("user1Id", a, "user2Id", b), bearerA), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful())
            .as("friendship create attendu 2xx — reçu %s, body=%s", post.getStatusCode(), post.getBody()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();

        // b (destinataire) voit la demande comme REÇUE
        ResponseEntity<String> received = restTemplate.exchange(
            url("/api/social/friendships/pending/by-user/" + b), HttpMethod.GET, jwtEntity(bearerB), String.class);
        assertThat(received.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(received.getBody()).contains(id);

        // a (auteur) ne voit PAS sa propre demande comme « reçue »
        ResponseEntity<String> sent = restTemplate.exchange(
            url("/api/social/friendships/pending/by-user/" + a), HttpMethod.GET, jwtEntity(bearerA), String.class);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sent.getBody()).doesNotContain(id);

        // RBAC : a ne peut pas lire les demandes reçues de b (requireOwnerOrAdmin) → 403
        assertThat(restTemplate.exchange(url("/api/social/friendships/pending/by-user/" + b), HttpMethod.GET, jwtEntity(bearerA), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // a (auteur) voit sa demande dans « ENVOYÉES »
        ResponseEntity<String> sentByA = restTemplate.exchange(
            url("/api/social/friendships/sent/by-user/" + a), HttpMethod.GET, jwtEntity(bearerA), String.class);
        assertThat(sentByA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sentByA.getBody()).contains(id);

        // b (destinataire) n'a RIEN envoyé → liste « envoyées » ne contient pas la demande
        assertThat(restTemplate.exchange(url("/api/social/friendships/sent/by-user/" + b), HttpMethod.GET, jwtEntity(bearerB), String.class)
            .getBody()).doesNotContain(id);

        // self-clean
        jdbc.update("DELETE FROM friendships WHERE id = ?::uuid", java.util.UUID.fromString(id));
        restTemplate.exchange(url("/api/users/" + a), HttpMethod.DELETE, jwtEntity(admin), String.class);
        restTemplate.exchange(url("/api/users/" + b), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void referrals_listAndCreate() {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/social/referrals?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/social/referrals/by-referrer/" + twoUsers().get(0)), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/social/referrals"), HttpMethod.POST,
            jsonJwtEntity(Map.of("referrerId", twoUsers().get(0), "referralCode", "OC-L4" + UUID.randomUUID().toString().substring(0, 6)), admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void favorites_add_list_delete() throws Exception {
        String admin = adminBearer();
        String uid = twoUsers().get(0);
        ResponseEntity<String> post = restTemplate.exchange(url("/api/social/favorites"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userId", uid, "restaurantId", restaurantId()), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/social/favorites/by-user/" + uid), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/social/favorites/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void friendGroups_fullFlow() throws Exception {
        String admin = adminBearer();
        List<String> u = twoUsers();
        ResponseEntity<String> post = restTemplate.exchange(url("/api/social/friend-groups"), HttpMethod.POST,
            jsonJwtEntity(Map.of("name", "Groupe L4"), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String groupId = om.readTree(post.getBody()).get("id").asText();

        assertThat(restTemplate.exchange(url("/api/social/friend-groups/" + groupId), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/social/friend-groups/by-owner/" + SEED_SUPERADMIN_ID), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/social/friend-groups/by-member/" + SEED_SUPERADMIN_ID), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/social/friend-groups/" + groupId), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("name", "Groupe L4 renommé"), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // members
        String friendId = u.get(1);
        assertThat(restTemplate.exchange(url("/api/social/friend-groups/" + groupId + "/members"), HttpMethod.POST,
            jsonJwtEntity(Map.of("friendId", friendId, "role", "member"), admin), String.class).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(restTemplate.exchange(url("/api/social/friend-groups/" + groupId + "/members"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/social/friend-groups/" + groupId + "/members/" + friendId), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();

        // self-clean
        assertThat(restTemplate.exchange(url("/api/social/friend-groups/" + groupId), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void friendGroup_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/social/friend-groups/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createFriendship_invalidBody_400() {
        assertThat(restTemplate.exchange(url("/api/social/friendships"), HttpMethod.POST,
            jsonJwtEntity(Map.of("user1Id", twoUsers().get(0)), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
