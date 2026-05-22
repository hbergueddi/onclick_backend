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

    @Test
    void friendships_create_accept_decline() throws Exception {
        String admin = adminBearer();
        List<String> u = twoUsers();
        // pré-nettoyage : la contrainte unique (user1_id,user2_id) bloquerait un re-run
        jdbc.update("DELETE FROM friendships WHERE (user1_id = ?::uuid AND user2_id = ?::uuid) OR (user1_id = ?::uuid AND user2_id = ?::uuid)",
            u.get(0), u.get(1), u.get(1), u.get(0));
        ResponseEntity<String> post = restTemplate.exchange(url("/api/social/friendships"), HttpMethod.POST,
            jsonJwtEntity(Map.of("user1Id", u.get(0), "user2Id", u.get(1)), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/social/friendships/by-user/" + u.get(0)), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/social/friendships/" + id + "/accept"), HttpMethod.PATCH, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/social/friendships/" + id + "/decline"), HttpMethod.PATCH, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
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
