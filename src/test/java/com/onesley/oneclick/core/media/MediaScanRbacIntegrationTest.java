package com.onesley.oneclick.core.media;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC de la ressource {@code MEDIA} pour les rôles qui scannent
 * (Snap2Earn) — couvre la migration {@code V101__grant_media_to_scanning_roles}.
 *
 * <p>Avant V101, un owner/staff qui scannait un ticket AVEC photo obtenait 403 sur
 * {@code POST /api/media/upload} (UPLOAD:MEDIA absent) → toast « accès refusé »,
 * alors que le crédit (snap2earn) passait. V101 octroie UPLOAD/CREATE/VIEW:MEDIA à
 * RESTAURATEUR / STAFF / GROUP_ADMIN. On vérifie ici, bout en bout via Spring
 * Security :
 *   - VIEW:MEDIA : RESTAURATEUR & STAFF listent (200) ; CLIENT (qui n'a QUE UPLOAD)
 *     reste 403 → prouve que le guard est réel et que V101 n'a pas sur-octroyé.
 *   - UPLOAD:MEDIA : RESTAURATEUR atteint le endpoint d'upload (≠ 403) → l'autorité
 *     octroyée par V101 laisse bien passer (le stockage S3/MinIO peut ensuite 201/500,
 *     mais plus jamais 403 = la cause du bug).
 */
class MediaScanRbacIntegrationTest extends AbstractIntegrationTest {

    @Test
    void restaurateur_canListMedia_viewGranted() {
        int status = restTemplate.exchange(
            url("/api/media"), HttpMethod.GET,
            jwtEntity(bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(200);
    }

    @Test
    void staff_canListMedia_viewGranted() {
        int status = restTemplate.exchange(
            url("/api/media"), HttpMethod.GET,
            jwtEntity(bearerForRole("STAFF")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(200);
    }

    @Test
    void client_cannotListMedia_lacksViewMedia() {
        // CLIENT a UPLOAD:MEDIA (avatar/photos) mais PAS VIEW:MEDIA → 403 (guard réel,
        // V101 n'élargit que RESTAURATEUR/STAFF/GROUP_ADMIN).
        int status = restTemplate.exchange(
            url("/api/media"), HttpMethod.GET,
            jwtEntity(bearerForRole("CLIENT")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void restaurateur_uploadEndpoint_authorized_notForbidden() {
        // Multipart valide (file + entityType + entityId) → le binding réussit et
        // l'on atteint @PreAuthorize('UPLOAD:MEDIA'). RESTAURATEUR est désormais
        // octroyé (V101) → l'autorisation passe : statut ≠ 403 (201 si stockage OK,
        // 4xx/5xx si MinIO indisponible en test — jamais 403, qui était le bug).
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource("fake-jpeg-bytes".getBytes()) {
            @Override public String getFilename() { return "ticket.jpg"; }
        });
        body.add("entityType", "scanned_ticket");
        body.add("entityId", UUID.randomUUID().toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerForRole("RESTAURATEUR"));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        int status = restTemplate.exchange(
            url("/api/media/upload"), HttpMethod.POST,
            new HttpEntity<>(body, headers), String.class)
            .getStatusCode().value();

        assertThat(status).isNotEqualTo(403);
    }
}
