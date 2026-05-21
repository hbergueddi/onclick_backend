package com.onesley.oneclick.validation;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration — Bean Validation des DTO d'entrée à travers la stack MVC réelle
 * (retour senior). Prouve que {@code @Valid @RequestBody} est bien câblé : un
 * payload invalide → 400 + champ nommé dans le {@code ProblemDetail}.
 *
 * <p>Zéro pollution DB : la validation rejette le payload pendant la résolution
 * d'argument, AVANT le service/persistance — d'où des UUID factices suffisants.
 */
class ValidationErrorIntegrationTest extends AbstractIntegrationTest {

    private static final String FAKE = "00000000-0000-0000-0000-000000000001";

    /** POST/PATCH JSON brut avec bearer SUPERADMIN ; retourne la réponse String. */
    private ResponseEntity<String> send(HttpMethod method, String path, String json) {
        return restTemplate.exchange(url(path), method, jsonJwtEntity(json, adminBearer()), String.class);
    }

    private void assert400WithField(ResponseEntity<String> r, String field) {
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody()).contains(field);
    }

    // ─── Bornes numériques (montants @DecimalMin, compteurs @PositiveOrZero/@Min) ─

    @Test
    void loyaltyEarn_negativeAmount_returns400() {
        assert400WithField(send(HttpMethod.POST, "/api/loyalty/earn",
            "{\"clientId\":\"" + FAKE + "\",\"restaurantId\":\"" + FAKE + "\",\"points\":5,\"amount\":-5,\"reason\":\"x\"}"),
            "amount");
    }

    @Test
    void event_negativeCapacity_returns400() {
        assert400WithField(send(HttpMethod.POST, "/api/events",
            "{\"tenantId\":\"" + FAKE + "\",\"title\":\"E2E\",\"eventAt\":\"2030-01-01T19:00:00Z\",\"capacity\":-1}"),
            "capacity");
    }

    @Test
    void restaurantCreate_latitudeOutOfRange_returns400() {
        assert400WithField(send(HttpMethod.POST, "/api/restaurants",
            "{\"tenantId\":\"" + FAKE + "\",\"name\":\"E2E\",\"city\":\"Casa\",\"latitude\":999}"),
            "latitude");
    }

    @Test
    void restaurantPatch_oversizedName_returns400() {
        assert400WithField(send(HttpMethod.PATCH, "/api/restaurants/" + FAKE,
            "{\"name\":\"" + "X".repeat(200) + "\"}"),
            "name");
    }

    @Test
    void cacheConfig_zeroMaxEntries_returns400() {
        assert400WithField(send(HttpMethod.POST, "/api/configuration/cache-configs",
            "{\"cacheName\":\"e2e\",\"ttlSeconds\":60,\"maxEntries\":0}"),
            "maxEntries");
    }

    @Test
    void media_negativeSizeBytes_returns400() {
        assert400WithField(send(HttpMethod.POST, "/api/media",
            "{\"entityType\":\"restaurant\",\"entityId\":\"" + FAKE + "\",\"url\":\"https://x/y.png\",\"mediaType\":\"image\",\"sizeBytes\":-1}"),
            "sizeBytes");
    }

    @Test
    void oneClickHiInvoice_negativeTotal_returns400() {
        assert400WithField(send(HttpMethod.POST, "/api/oneclickhi/invoices",
            "{\"tenantId\":\"" + FAKE + "\",\"restaurantId\":\"" + FAKE + "\",\"invoiceNumber\":\"INV-1\",\"periodMonth\":\"2026-05\",\"totalAmount\":-1}"),
            "totalAmount");
    }

    @Test
    void resourcePricing_negativeDuration_returns400() {
        assert400WithField(send(HttpMethod.POST, "/api/resource-bookings/pricings",
            "{\"resourceId\":\"" + FAKE + "\",\"name\":\"90 min\",\"price\":200,\"durationMinutes\":-1}"),
            "durationMinutes");
    }

    // ─── Edge cases : JSON malformé, champ requis manquant, mauvais type ───────

    @Test
    void malformedJson_returns400() {
        ResponseEntity<String> r = send(HttpMethod.POST, "/api/loyalty/earn", "{\"amount\": }");
        assertThat(r.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void missingRequiredField_returns400() {
        // tenantId @NotNull + name @NotBlank manquants
        assert400WithField(send(HttpMethod.POST, "/api/restaurants", "{\"city\":\"Casa\"}"), "name");
    }

    @Test
    void wrongFieldType_returns400() {
        // clientId attendu UUID, on envoie un entier
        ResponseEntity<String> r = send(HttpMethod.POST, "/api/loyalty/earn",
            "{\"clientId\":12345,\"restaurantId\":\"" + FAKE + "\",\"points\":5}");
        assertThat(r.getStatusCode().value()).isEqualTo(400);
    }
}
