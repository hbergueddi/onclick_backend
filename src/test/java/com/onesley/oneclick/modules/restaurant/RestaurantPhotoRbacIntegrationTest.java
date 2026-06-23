package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.core.media.internal.MediaStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Intégration — galerie photos « Identité visuelle » d'un restaurant (max 5).
 *
 * <p>Contrat testé :
 * <ul>
 *   <li>POST photo en CLIENT → 403 (pas d'autorité {@code UPDATE:RESTAURANTS}).</li>
 *   <li>Flux admin : upload (1re = principale) → 2e photo → liste (principale en tête)
 *       → galerie exposée sur {@code GET /api/restaurants/{id}} (Spotlight) + {@code image}
 *       synchronisée → set-primary (resync image) → delete.</li>
 *   <li>Quota : la 6e photo → 400.</li>
 * </ul>
 * L'ABAC fine « staff actif du resto » est couverte par le test unitaire ; l'admin (bypass)
 * valide ici le chemin d'écriture + le RBAC grossier. Le stockage S3/MinIO est mocké
 * ({@link MediaStorageService}) → aucun service externe réel (tests reproductibles).
 */
class RestaurantPhotoRbacIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    MediaStorageService storage;

    private UUID restaurantId;
    private int uploadSeq;

    @BeforeEach
    void setup() {
        UUID tenantId = UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = 'oneclick' AND deleted_at IS NULL LIMIT 1", String.class));
        restaurantId = UUID.randomUUID();
        jdbc.update("INSERT INTO restaurants (id, tenant_id, name, city) VALUES (?, ?, ?, ?)",
            restaurantId, tenantId, "PHOTO-Resto-" + restaurantId, "Casablanca");
        // Chaque upload renvoie une URL unique simulée (pas d'accès S3 réel).
        uploadSeq = 0;
        when(storage.upload(any(), any(), any()))
            .thenAnswer(inv -> "https://cdn.test/" + restaurantId + "/" + (++uploadSeq) + ".jpg");
    }

    @AfterEach
    void cleanup() {
        if (restaurantId != null) {
            jdbc.update("DELETE FROM medias WHERE entity_type = 'restaurant' AND entity_id = ?", restaurantId);
            jdbc.update("DELETE FROM restaurants WHERE id = ?", restaurantId);
        }
        restaurantId = null;
    }

    /** Multipart « file » (+ makePrimary optionnel) avec Bearer. */
    private HttpEntity<MultiValueMap<String, Object>> photoUpload(String jwt, Boolean makePrimary) {
        HttpHeaders headers = new HttpHeaders();
        if (jwt != null) headers.setBearerAuth(jwt);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ByteArrayResource res = new ByteArrayResource("fake-jpeg-bytes".getBytes()) {
            @Override public String getFilename() { return "photo.jpg"; }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.IMAGE_JPEG);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(res, partHeaders));
        if (makePrimary != null) body.add("makePrimary", makePrimary.toString());
        return new HttpEntity<>(body, headers);
    }

    @Test
    void addPhoto_client_returns403() {
        int status = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId + "/photos"),
            HttpMethod.POST, photoUpload(bearerForRole("CLIENT"), null), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void photoFlow_admin_uploadListPrimaryDelete_exposedOnSpotlight() {
        String admin = adminBearer();

        // 1re photo → devient la principale (synchro restaurants.image).
        ResponseEntity<Map> p1 = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId + "/photos"),
            HttpMethod.POST, photoUpload(admin, null), Map.class);
        assertThat(p1.getStatusCode().value()).isEqualTo(201);
        assertThat(p1.getBody().get("primary")).isEqualTo(true);
        String url1 = (String) p1.getBody().get("url");

        // 2e photo → secondaire.
        ResponseEntity<Map> p2 = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId + "/photos"),
            HttpMethod.POST, photoUpload(admin, null), Map.class);
        assertThat(p2.getStatusCode().value()).isEqualTo(201);
        assertThat(p2.getBody().get("primary")).isEqualTo(false);
        String mediaId2 = (String) p2.getBody().get("id");
        String url2 = (String) p2.getBody().get("url");

        // Liste de gestion : 2 photos, principale en tête.
        ResponseEntity<List> list = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId + "/photos"),
            HttpMethod.GET, jwtEntity(admin), List.class);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
        assertThat(list.getBody()).hasSize(2);
        assertThat(((Map) list.getBody().get(0)).get("primary")).isEqualTo(true);

        // Fiche Spotlight (PUBLIC) : galerie exposée + image = principale (url1).
        ResponseEntity<Map> detail = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId),
            HttpMethod.GET, jwtEntity(null), Map.class);
        assertThat(detail.getStatusCode().value()).isEqualTo(200);
        assertThat((List) detail.getBody().get("photos")).hasSize(2);
        assertThat(detail.getBody().get("image")).isEqualTo(url1);

        // set-primary sur la 2e → image resynchronisée (url2).
        ResponseEntity<List> afterPrimary = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId + "/photos/" + mediaId2 + "/primary"),
            HttpMethod.PATCH, jwtEntity(admin), List.class);
        assertThat(afterPrimary.getStatusCode().value()).isEqualTo(200);
        ResponseEntity<Map> detail2 = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId),
            HttpMethod.GET, jwtEntity(null), Map.class);
        assertThat(detail2.getBody().get("image")).isEqualTo(url2);

        // delete la 2e → 1 photo restante.
        ResponseEntity<List> afterDelete = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId + "/photos/" + mediaId2),
            HttpMethod.DELETE, jwtEntity(admin), List.class);
        assertThat(afterDelete.getStatusCode().value()).isEqualTo(200);
        assertThat(afterDelete.getBody()).hasSize(1);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void addPhoto_sixth_returns400_quotaEnforced() {
        String admin = adminBearer();
        for (int i = 0; i < 5; i++) {
            int s = restTemplate.exchange(
                url("/api/restaurants/" + restaurantId + "/photos"),
                HttpMethod.POST, photoUpload(admin, null), Map.class).getStatusCode().value();
            assertThat(s).isEqualTo(201);
        }
        int sixth = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId + "/photos"),
            HttpMethod.POST, photoUpload(admin, null), String.class).getStatusCode().value();
        assertThat(sixth).isEqualTo(400);
    }
}
