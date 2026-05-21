package com.onesley.oneclick.modules.oneclickhi;

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
 * L4 « profondeur » — flux end-to-end {@code /api/oneclickhi}.
 * Invoices CRUD + cockpit + restaurant-hi + charts + erreurs.
 */
class OneClickHIFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private String[] restoTenant() {
        return jdbc.queryForObject(
            "SELECT r.id::text || ',' || r.tenant_id::text FROM restaurants r WHERE r.tenant_id IS NOT NULL AND r.deleted_at IS NULL LIMIT 1",
            String.class).split(",");
    }

    @Test
    void invoice_fullLifecycle_andAggregates() throws Exception {
        String admin = adminBearer();
        String[] rt = restoTenant();
        String restaurantId = rt[0], tenantId = rt[1];

        // CREATE
        ResponseEntity<String> post = restTemplate.exchange(url("/api/oneclickhi/invoices"), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "tenantId", tenantId, "restaurantId", restaurantId,
                "invoiceNumber", "L4-" + UUID.randomUUID().toString().substring(0, 8),
                "periodMonth", "2026-05", "totalAmount", 1000, "vatAmount", 200,
                "pdfUrl", "http://pdf/l4", "credit3pct", 30), admin), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = om.readTree(post.getBody()).get("id").asText();

        // GET + list
        assertThat(restTemplate.exchange(url("/api/oneclickhi/invoices/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/oneclickhi/invoices"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // PATCH status → paid
        ResponseEntity<String> patch = restTemplate.exchange(url("/api/oneclickhi/invoices/" + id), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "paid"), admin), String.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(patch.getBody()).get("status").asText()).isEqualTo("paid");

        // aggregates
        assertThat(restTemplate.exchange(url("/api/oneclickhi/cockpit"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/oneclickhi/restaurant-hi/" + restaurantId), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/oneclickhi/restaurant-hi/" + restaurantId + "/charts?months=12"),
            HttpMethod.GET, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // DELETE → 204 (soft-delete). NB: findById ne filtre PAS deletedAt (invoices
        // restent récupérables par id pour l'audit) — elles disparaissent juste de la
        // liste active findAllActive.
        assertThat(restTemplate.exchange(url("/api/oneclickhi/invoices/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> activeList = restTemplate.exchange(url("/api/oneclickhi/invoices"),
            HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(activeList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activeList.getBody()).doesNotContain(id);
    }

    @Test
    void invoice_unknownId_404() {
        assertThat(restTemplate.exchange(url("/api/oneclickhi/invoices/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createInvoice_negativeAmount_400() {
        String[] rt = restoTenant();
        assertThat(restTemplate.exchange(url("/api/oneclickhi/invoices"), HttpMethod.POST,
            jsonJwtEntity(Map.of("tenantId", rt[1], "restaurantId", rt[0], "totalAmount", -5), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createInvoice_asClient_403() {
        String[] rt = restoTenant();
        assertThat(restTemplate.exchange(url("/api/oneclickhi/invoices"), HttpMethod.POST,
            jsonJwtEntity(Map.of("tenantId", rt[1], "restaurantId", rt[0], "invoiceNumber", "X", "periodMonth", "2026-05"),
                bearerForRole("CLIENT")), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
