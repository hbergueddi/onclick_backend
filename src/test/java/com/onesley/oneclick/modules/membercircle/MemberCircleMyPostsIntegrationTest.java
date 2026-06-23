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
 * Intégration C9 — « Mes posts en attente » + suppression de son propre post (membercircle).
 *
 * <p>Stack réelle (HTTP + JWT + filter chain → controller → service → {@code oneclick_enterprise}).
 * Vérifie :
 * <ul>
 *   <li>{@code GET /api/member-posts/mine/pending} ne renvoie QUE mes posts non approuvés
 *       (pending/rejected), avec le statut exposé ; un post approuvé n'y figure pas, et le post
 *       d'un AUTRE membre non plus (ABAC self par construction = author_id du JWT) ;</li>
 *   <li>{@code DELETE /api/member-posts/mine/{id}} : l'auteur supprime son post pending → 204 ;</li>
 *   <li>ABAC : un AUTRE membre (qui détient pourtant {@code DELETE:COMMUNITY}) → 403 ;</li>
 *   <li>un post APPROUVÉ ne peut pas être supprimé par son auteur → 409 ;</li>
 *   <li>anonyme → 401.</li>
 * </ul>
 *
 * <p>Non destructif : tous les posts créés sont purgés en {@code finally}.
 */
class MemberCircleMyPostsIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private record Member(UUID id, UUID tenantId) {}

    private Member aTenantClient() {
        String[] p = jdbc.queryForObject(
            "SELECT u.id::text || ',' || u.tenant_id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.tenant_id IS NOT NULL AND u.deleted_at IS NULL LIMIT 1",
            String.class).split(",");
        return new Member(UUID.fromString(p[0]), UUID.fromString(p[1]));
    }

    private Member anotherTenantClient(UUID exclude) {
        String[] p = jdbc.queryForObject(
            "SELECT u.id::text || ',' || u.tenant_id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.tenant_id IS NOT NULL AND u.deleted_at IS NULL AND u.id <> ?::uuid LIMIT 1",
            String.class, exclude).split(",");
        return new Member(UUID.fromString(p[0]), UUID.fromString(p[1]));
    }

    private void purge(UUID postId) {
        if (postId == null) return;
        jdbc.update("DELETE FROM member_post_comments WHERE post_id = ?::uuid", postId);
        jdbc.update("DELETE FROM member_post_likes WHERE post_id = ?::uuid", postId);
        jdbc.update("DELETE FROM member_posts WHERE id = ?::uuid", postId);
    }

    /** Crée un post membre (status=pending par construction serveur) et renvoie son id. */
    private UUID createPost(String bearer, String content) throws Exception {
        ResponseEntity<String> created = restTemplate.exchange(
            url("/api/member-posts"), HttpMethod.POST,
            jsonJwtEntity(Map.of("content", content), bearer), String.class);
        assertThat(created.getStatusCode())
            .as("create reçu %s body=%s", created.getStatusCode(), created.getBody())
            .isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(om.readTree(created.getBody()).get("id").asText());
    }

    private JsonNode minePending(String bearer) throws Exception {
        ResponseEntity<String> r = restTemplate.exchange(
            url("/api/member-posts/mine/pending"), HttpMethod.GET, jwtEntity(bearer), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return om.readTree(r.getBody());
    }

    private boolean contains(JsonNode arr, UUID postId) {
        for (JsonNode n : arr) {
            if (postId.toString().equals(n.get("id").asText())) return true;
        }
        return false;
    }

    @Test
    void minePending_returnsOnlyMyNonApprovedPosts_withStatus() throws Exception {
        Member m = aTenantClient();
        String bearer = jwtIssuer.issueAccessToken(m.id(), "CLIENT").token();
        UUID pendingId = null, rejectedId = null, approvedId = null, otherId = null;
        try {
            pendingId = createPost(bearer, "C9 mon post pending");
            rejectedId = createPost(bearer, "C9 mon post rejected");
            jdbc.update("UPDATE member_posts SET status = 'rejected', rejection_reason = 'hors charte' "
                + "WHERE id = ?::uuid", rejectedId);
            approvedId = createPost(bearer, "C9 mon post approuvé");
            jdbc.update("UPDATE member_posts SET status = 'approved' WHERE id = ?::uuid", approvedId);

            // Un AUTRE membre publie : ne doit JAMAIS apparaître dans MON /mine/pending.
            Member other = anotherTenantClient(m.id());
            String otherBearer = jwtIssuer.issueAccessToken(other.id(), "CLIENT").token();
            otherId = createPost(otherBearer, "C9 post d'autrui pending");

            JsonNode mine = minePending(bearer);
            assertThat(contains(mine, pendingId)).as("mon pending présent").isTrue();
            assertThat(contains(mine, rejectedId)).as("mon rejected présent").isTrue();
            assertThat(contains(mine, approvedId)).as("mon approuvé absent").isFalse();
            assertThat(contains(mine, otherId)).as("post d'autrui absent (ABAC self)").isFalse();

            // Statut + motif de rejet exposés (le membre voit pourquoi).
            for (JsonNode n : mine) {
                if (rejectedId.toString().equals(n.get("id").asText())) {
                    assertThat(n.get("status").asText()).isEqualTo("rejected");
                    assertThat(n.get("rejectionReason").asText()).isEqualTo("hors charte");
                }
                if (pendingId.toString().equals(n.get("id").asText())) {
                    assertThat(n.get("status").asText()).isEqualTo("pending");
                }
                // jamais d'approuvé dans cette liste
                assertThat(n.get("status").asText()).isNotEqualTo("approved");
            }
        } finally {
            purge(pendingId);
            purge(rejectedId);
            purge(approvedId);
            purge(otherId);
        }
    }

    @Test
    void minePending_noToken_returns401() {
        int status = restTemplate.exchange(
            url("/api/member-posts/mine/pending"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(401);
    }

    @Test
    void deleteOwnPost_byAuthor_pending_returns204_andUnlisted() throws Exception {
        Member m = aTenantClient();
        String bearer = jwtIssuer.issueAccessToken(m.id(), "CLIENT").token();
        UUID postId = null;
        try {
            postId = createPost(bearer, "C9 post à supprimer");
            int status = restTemplate.exchange(
                url("/api/member-posts/mine/" + postId), HttpMethod.DELETE, jwtEntity(bearer), Void.class)
                .getStatusCode().value();
            assertThat(status).isEqualTo(204);
            // Soft-delete effectif : disparaît de /mine/pending.
            assertThat(contains(minePending(bearer), postId)).as("supprimé absent de /mine/pending").isFalse();
            assertThat(jdbc.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM member_posts WHERE id = ?::uuid", Boolean.class, postId))
                .as("deleted_at posé en DB").isTrue();
        } finally {
            purge(postId);
        }
    }

    @Test
    void deleteOwnPost_byOtherMember_returns403() throws Exception {
        Member author = aTenantClient();
        String authorBearer = jwtIssuer.issueAccessToken(author.id(), "CLIENT").token();
        Member other = anotherTenantClient(author.id());
        String otherBearer = jwtIssuer.issueAccessToken(other.id(), "CLIENT").token();
        UUID postId = null;
        try {
            postId = createPost(authorBearer, "C9 post intouchable par autrui");
            // other détient DELETE:COMMUNITY mais n'est PAS l'auteur → 403 (ABAC).
            int status = restTemplate.exchange(
                url("/api/member-posts/mine/" + postId), HttpMethod.DELETE, jwtEntity(otherBearer), Void.class)
                .getStatusCode().value();
            assertThat(status).isEqualTo(403);
            assertThat(jdbc.queryForObject(
                "SELECT deleted_at IS NULL FROM member_posts WHERE id = ?::uuid", Boolean.class, postId))
                .as("post intact").isTrue();
        } finally {
            purge(postId);
        }
    }

    @Test
    void deleteOwnPost_approvedPost_returns409() throws Exception {
        Member m = aTenantClient();
        String bearer = jwtIssuer.issueAccessToken(m.id(), "CLIENT").token();
        UUID postId = null;
        try {
            postId = createPost(bearer, "C9 post approuvé non auto-supprimable");
            jdbc.update("UPDATE member_posts SET status = 'approved' WHERE id = ?::uuid", postId);
            int status = restTemplate.exchange(
                url("/api/member-posts/mine/" + postId), HttpMethod.DELETE, jwtEntity(bearer), Void.class)
                .getStatusCode().value();
            assertThat(status).isEqualTo(409);
            assertThat(jdbc.queryForObject(
                "SELECT deleted_at IS NULL FROM member_posts WHERE id = ?::uuid", Boolean.class, postId))
                .as("post approuvé intact").isTrue();
        } finally {
            purge(postId);
        }
    }

    @Test
    void deleteOwnPost_noToken_returns401() {
        int status = restTemplate.exchange(
            url("/api/member-posts/mine/" + UUID.randomUUID()), HttpMethod.DELETE, jwtEntity(null), Void.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(401);
    }
}
