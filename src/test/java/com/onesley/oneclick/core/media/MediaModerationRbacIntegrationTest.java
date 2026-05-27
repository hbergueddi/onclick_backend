package com.onesley.oneclick.core.media;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC + modération de la ressource {@code MEDIA_MODERATION} (V46,
 * statut sur la table polymorphique medias). Admin-only (SUPERADMIN) ; la
 * ressource est DISTINCTE de MEDIA (dont le CLIENT a UPLOAD) → RESTAURATEUR/CLIENT
 * obtiennent 403 sur la modération.
 */
class MediaModerationRbacIntegrationTest extends AbstractIntegrationTest {

    private UUID mediaId;

    @BeforeEach
    void seedMedia() {
        mediaId = UUID.fromString(jdbc.queryForObject(
            "INSERT INTO medias (entity_type, entity_id, url, media_type) "
            + "VALUES ('restaurant', gen_random_uuid(), 'http://example.test/mod.jpg', 'image') "
            + "RETURNING id::text", String.class));
    }

    @AfterEach
    void cleanup() {
        if (mediaId != null) jdbc.update("DELETE FROM medias WHERE id = ?", mediaId);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void superadmin_listAndModerate() {
        String admin = adminBearer();

        ResponseEntity<Map[]> list = restTemplate.exchange(
            url("/api/media/moderation?entityType=restaurant"), HttpMethod.GET, jwtEntity(admin), Map[].class);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
        assertThat(Arrays.stream(list.getBody())
            .anyMatch(m -> mediaId.toString().equals(m.get("id")))).isTrue();

        ResponseEntity<Map> patched = restTemplate.exchange(
            url("/api/media/" + mediaId + "/moderation"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "rejected"), admin), Map.class);
        assertThat(patched.getStatusCode().value()).isEqualTo(200);
        assertThat(patched.getBody().get("moderationStatus")).isEqualTo("rejected");
    }

    @Test
    void restaurateur_list_returns403_adminOnly() {
        int status = restTemplate.exchange(
            url("/api/media/moderation"), HttpMethod.GET,
            jwtEntity(bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void client_moderate_returns403_adminOnly() {
        int status = restTemplate.exchange(
            url("/api/media/" + mediaId + "/moderation"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "approved"), bearerForRole("CLIENT")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }
}
