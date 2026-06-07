package com.onesley.oneclick.modules.membercircle;

import com.fasterxml.jackson.databind.JsonNode;
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

/**
 * Intégration A.2 — commentaires + mentions du mur communautaire.
 *
 * <p>Flux : un CLIENT crée un post → modération (jdbc approve) → commente (+ mention) →
 * le commentaire est listé (enrichi auteur), le feed reflète commentsCount, et l'autocomplete
 * des membres mentionnables répond. RBAC : commenter sans token → 401 ; commenter un post
 * non approuvé → 400. Non destructif : posts/commentaires purgés en {@code finally}.
 */
class MemberCircleCommentsIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private record Member(UUID id, UUID tenantId) {}

    private Member aTenantClient() {
        String[] p = jdbc.queryForObject(
            "SELECT u.id::text || ',' || u.tenant_id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.tenant_id IS NOT NULL AND u.deleted_at IS NULL LIMIT 1",
            String.class).split(",");
        return new Member(UUID.fromString(p[0]), UUID.fromString(p[1]));
    }

    private void purge(UUID postId) {
        jdbc.update("DELETE FROM member_post_comments WHERE post_id = ?::uuid", postId);
        jdbc.update("DELETE FROM member_post_likes WHERE post_id = ?::uuid", postId);
        jdbc.update("DELETE FROM member_posts WHERE id = ?::uuid", postId);
    }

    private UUID createApprovedPost(String bearer) throws Exception {
        ResponseEntity<String> created = restTemplate.exchange(
            url("/api/member-posts"), HttpMethod.POST,
            jsonJwtEntity(Map.of("content", "Post à commenter"), bearer), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = UUID.fromString(om.readTree(created.getBody()).get("id").asText());
        jdbc.update("UPDATE member_posts SET status = 'approved' WHERE id = ?::uuid", id);
        return id;
    }

    @Test
    void member_commentsApprovedPost_withMention_listedAndCounted() throws Exception {
        Member m = aTenantClient();
        String bearer = jwtIssuer.issueAccessToken(m.id(), "CLIENT").token();
        UUID postId = null;
        try {
            postId = createApprovedPost(bearer);
            UUID mentioned = UUID.randomUUID();

            // POST comment (+ mention) → 201.
            ResponseEntity<String> c = restTemplate.exchange(
                url("/api/member-posts/" + postId + "/comments"), HttpMethod.POST,
                jsonJwtEntity(Map.of("content", "Bien joué @membre !",
                    "mentionedUserIds", List.of(mentioned.toString())), bearer), String.class);
            assertThat(c.getStatusCode())
                .as("comment reçu %s body=%s", c.getStatusCode(), c.getBody())
                .isEqualTo(HttpStatus.CREATED);
            assertThat(om.readTree(c.getBody()).get("content").asText()).isEqualTo("Bien joué @membre !");
            assertThat(om.readTree(c.getBody()).get("mentionedUserIds").get(0).asText())
                .isEqualTo(mentioned.toString());

            // GET comments → 1 commentaire enrichi.
            ResponseEntity<String> list = restTemplate.exchange(
                url("/api/member-posts/" + postId + "/comments"), HttpMethod.GET, jwtEntity(bearer), String.class);
            assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(om.readTree(list.getBody())).hasSize(1);

            // GET feed → commentsCount = 1 pour ce post.
            ResponseEntity<String> feed = restTemplate.exchange(
                url("/api/member-posts/feed"), HttpMethod.GET, jwtEntity(bearer), String.class);
            JsonNode mine = null;
            for (JsonNode n : om.readTree(feed.getBody())) {
                if (postId.toString().equals(n.get("id").asText())) { mine = n; break; }
            }
            assertThat(mine).isNotNull();
            assertThat(mine.get("commentsCount").asLong()).isEqualTo(1);
        } finally {
            if (postId != null) purge(postId);
        }
    }

    @Test
    void mentionable_returnsTenantMembers_excludingSelf() throws Exception {
        Member m = aTenantClient();
        String bearer = jwtIssuer.issueAccessToken(m.id(), "CLIENT").token();
        ResponseEntity<String> r = restTemplate.exchange(
            url("/api/member-posts/mentionable?q="), HttpMethod.GET, jwtEntity(bearer), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode arr = om.readTree(r.getBody());
        assertThat(arr.isArray()).isTrue();
        for (JsonNode n : arr) {
            assertThat(n.get("id").asText()).isNotEqualTo(m.id().toString()); // self exclu
        }
    }

    @Test
    void comment_noToken_returns401() {
        // post id bidon : la sécurité (401) prime sur l'existence du post.
        int status = restTemplate.exchange(
            url("/api/member-posts/" + UUID.randomUUID() + "/comments"), HttpMethod.POST,
            jsonJwtEntity(Map.of("content", "x"), null), String.class).getStatusCode().value();
        assertThat(status).isEqualTo(401);
    }

    @Test
    void comment_pendingPost_returns400() throws Exception {
        Member m = aTenantClient();
        String bearer = jwtIssuer.issueAccessToken(m.id(), "CLIENT").token();
        UUID postId = null;
        try {
            // post créé mais NON approuvé (reste pending).
            ResponseEntity<String> created = restTemplate.exchange(
                url("/api/member-posts"), HttpMethod.POST,
                jsonJwtEntity(Map.of("content", "Post pending"), bearer), String.class);
            postId = UUID.fromString(om.readTree(created.getBody()).get("id").asText());
            int status = restTemplate.exchange(
                url("/api/member-posts/" + postId + "/comments"), HttpMethod.POST,
                jsonJwtEntity(Map.of("content", "commentaire interdit"), bearer), String.class)
                .getStatusCode().value();
            assertThat(status).isEqualTo(400);
        } finally {
            if (postId != null) purge(postId);
        }
    }
}
