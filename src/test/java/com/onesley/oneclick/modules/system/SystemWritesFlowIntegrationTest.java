package com.onesley.oneclick.modules.system;

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

/** L4 « profondeur » — {@code /api/system} écritures : documents (upsert+versions) + custom-roles. */
class SystemWritesFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void documents_upsert_get_versions() throws Exception {
        String admin = adminBearer();
        String docId = "l4-doc-" + UUID.randomUUID().toString().substring(0, 8);

        assertThat(restTemplate.exchange(url("/api/system/documents/" + docId), HttpMethod.PUT,
            jsonJwtEntity(Map.of("content", "Contenu L4 v1", "version", "1.0"), admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/system/documents/" + docId), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/system/documents/" + docId + "/versions"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/system/documents/" + docId + "/versions"), HttpMethod.POST,
            jsonJwtEntity(Map.of("version", "1.1", "content", "Contenu L4 v1.1", "notes", "maj L4"), admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void healthCheck_record_201() {
        ResponseEntity<String> r = restTemplate.exchange(url("/api/system/health-checks"), HttpMethod.POST,
            jsonJwtEntity(Map.of("component", "database", "status", "up", "latencyMs", 12), adminBearer()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void quotaChangeLog_record_201() {
        String restaurantId = jdbc.queryForObject("SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class);
        ResponseEntity<String> r = restTemplate.exchange(url("/api/system/quota-change-logs"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restaurantId, "quotaType", "max_staff",
                "oldValue", 10, "newValue", 15, "reason", "upgrade L4"), adminBearer()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void customRoles_create_delete() throws Exception {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/system/custom-roles"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> post = restTemplate.exchange(url("/api/system/custom-roles"), HttpMethod.POST,
            jsonJwtEntity(Map.of("name", "Rôle L4 " + UUID.randomUUID().toString().substring(0, 6),
                "description", "rôle de test", "permissions", List.of("VIEW:RESTAURANTS")), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/system/custom-roles/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
