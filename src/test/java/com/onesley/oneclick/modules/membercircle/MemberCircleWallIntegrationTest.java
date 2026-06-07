package com.onesley.oneclick.modules.membercircle;

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
 * Intégration A.1 — flux MEMBRE du mur communautaire (création / feed approuvé / like).
 *
 * <p>Stack réelle (HTTP + JWT + filter chain → controller → service → {@code oneclick_enterprise}).
 * Vérifie : un CLIENT publie (status=pending, tenant résolu serveur), le feed ne montre que les
 * posts APPROUVÉS (du tenant du membre, enrichis auteur + likes), le toggle like est idempotent,
 * et l'anonyme est rejeté (401). Non destructif : posts/likes créés sont purgés en {@code finally}.
 */
class MemberCircleWallIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private record Member(UUID id, UUID tenantId) {}

    /** Un CLIENT rattaché à un tenant (create résout le tenant via l'auteur). */
    private Member aTenantClient() {
        String[] p = jdbc.queryForObject(
            "SELECT u.id::text || ',' || u.tenant_id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.tenant_id IS NOT NULL AND u.deleted_at IS NULL LIMIT 1",
            String.class).split(",");
        return new Member(UUID.fromString(p[0]), UUID.fromString(p[1]));
    }

    private void purge(UUID postId) {
        jdbc.update("DELETE FROM member_post_likes WHERE post_id = ?::uuid", postId);
        jdbc.update("DELETE FROM member_posts WHERE id = ?::uuid", postId);
    }

    @Test
    void member_createsPost_feedShowsApprovedOnly_andLikeToggles() throws Exception {
        Member m = aTenantClient();
        String bearer = jwtIssuer.issueAccessToken(m.id(), "CLIENT").token();
        UUID postId = null;
        try {
            // 1) Création membre → 201, status pending, tenant résolu serveur.
            ResponseEntity<String> created = restTemplate.exchange(
                url("/api/member-posts"), HttpMethod.POST,
                jsonJwtEntity(Map.of("content", "Super session padel ce matin", "activityTag", "padel"), bearer),
                String.class);
            assertThat(created.getStatusCode())
                .as("create reçu %s body=%s", created.getStatusCode(), created.getBody())
                .isEqualTo(HttpStatus.CREATED);
            postId = UUID.fromString(om.readTree(created.getBody()).get("id").asText());
            assertThat(om.readTree(created.getBody()).get("status").asText()).isEqualTo("pending");
            assertThat(jdbc.queryForObject(
                "SELECT tenant_id::text FROM member_posts WHERE id = ?::uuid", String.class, postId))
                .isEqualTo(m.tenantId().toString());

            // 2) Feed : le post pending n'apparaît PAS (approuvés seulement).
            assertThat(feedContains(bearer, postId)).as("pending absent du feed").isFalse();

            // 3) Modération (simulée en DB) → approuvé → présent dans le feed.
            jdbc.update("UPDATE member_posts SET status = 'approved' WHERE id = ?::uuid", postId);
            JsonNode feedPost = feedFind(bearer, postId);
            assertThat(feedPost).as("approuvé présent dans le feed").isNotNull();
            assertThat(feedPost.get("likesCount").asLong()).isZero();
            assertThat(feedPost.get("likedByMe").asBoolean()).isFalse();

            // 4) Like toggle : like → true/1 ; re-like → false/0.
            ResponseEntity<String> like1 = restTemplate.exchange(
                url("/api/member-posts/" + postId + "/like"), HttpMethod.POST, jwtEntity(bearer), String.class);
            assertThat(like1.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(om.readTree(like1.getBody()).get("liked").asBoolean()).isTrue();
            assertThat(om.readTree(like1.getBody()).get("likesCount").asLong()).isEqualTo(1);
            assertThat(feedFind(bearer, postId).get("likedByMe").asBoolean()).isTrue();

            String like2 = restTemplate.exchange(
                url("/api/member-posts/" + postId + "/like"), HttpMethod.POST, jwtEntity(bearer), String.class)
                .getBody();
            assertThat(om.readTree(like2).get("liked").asBoolean()).isFalse();
            assertThat(om.readTree(like2).get("likesCount").asLong()).isZero();
        } finally {
            if (postId != null) purge(postId);
        }
    }

    @Test
    void create_noToken_returns401() {
        int status = restTemplate.exchange(url("/api/member-posts"), HttpMethod.POST,
            jsonJwtEntity(Map.of("content", "x"), null), String.class).getStatusCode().value();
        assertThat(status).isEqualTo(401);
    }

    // ─── helpers feed ─────────────────────────────────────────────────────────

    private JsonNode feedFind(String bearer, UUID postId) throws Exception {
        ResponseEntity<String> feed = restTemplate.exchange(
            url("/api/member-posts/feed"), HttpMethod.GET, jwtEntity(bearer), String.class);
        assertThat(feed.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (JsonNode n : om.readTree(feed.getBody())) {
            if (postId.toString().equals(n.get("id").asText())) return n;
        }
        return null;
    }

    private boolean feedContains(String bearer, UUID postId) throws Exception {
        return feedFind(bearer, postId) != null;
    }
}
