package com.onesley.oneclick.modules.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/community} : posts + comments + likes, self-clean. */
class CommunityFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String userId() { return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class); }

    @Test
    void community_fullFlow() throws Exception {
        String admin = adminBearer();
        String uid = userId();

        ResponseEntity<String> post = restTemplate.exchange(url("/api/community/posts"), HttpMethod.POST,
            jsonJwtEntity(Map.of("authorId", uid, "content", "Post L4", "visibility", "public"), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String postId = om.readTree(post.getBody()).get("id").asText();

        assertThat(restTemplate.exchange(url("/api/community/posts?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/community/posts/" + postId), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(restTemplate.exchange(url("/api/community/comments"), HttpMethod.POST,
            jsonJwtEntity(Map.of("postId", postId, "authorId", uid, "content", "Commentaire L4"), admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(restTemplate.exchange(url("/api/community/posts/" + postId + "/comments"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(restTemplate.exchange(url("/api/community/likes"), HttpMethod.POST,
            jsonJwtEntity(Map.of("postId", postId, "userId", uid), admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(restTemplate.exchange(url("/api/community/posts/" + postId + "/likes"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // DELETE post → 404 (self-clean)
        assertThat(restTemplate.exchange(url("/api/community/posts/" + postId), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.exchange(url("/api/community/posts/" + postId), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getPost_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/community/posts/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createPost_invalidBody_400() {
        assertThat(restTemplate.exchange(url("/api/community/posts"), HttpMethod.POST,
            jsonJwtEntity(Map.of("visibility", "public"), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/community/posts?page=0&size=5"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
