package com.onesley.oneclick.modules.analytics.search.es;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du document ES {@link RestaurantEsDoc} — constructeur de projection
 * (Restaurant → doc dénormalisé) + accesseurs. Non exercé par les tests d'intégration
 * car Elasticsearch est désactivé dans l'env de test.
 */
class RestaurantEsDocTest {

    @Test
    void projectionConstructor_mapsAllFields_andStampsIndexedAt() {
        UUID id = UUID.randomUUID(), tenant = UUID.randomUUID();
        RestaurantEsDoc d = new RestaurantEsDoc(id, tenant, "Le Resto", "Casablanca",
            "12 rue X", "cuisine marocaine", "+212600000000", "active");
        assertThat(d.getId()).isEqualTo(id.toString()); // UUID stringifié
        assertThat(d.getTenantId()).isEqualTo(tenant);
        assertThat(d.getName()).isEqualTo("Le Resto");
        assertThat(d.getCity()).isEqualTo("Casablanca");
        assertThat(d.getAddress()).isEqualTo("12 rue X");
        assertThat(d.getDescription()).isEqualTo("cuisine marocaine");
        assertThat(d.getPhone()).isEqualTo("+212600000000");
        assertThat(d.getStatus()).isEqualTo("active");
        assertThat(d.getIndexedAt()).isNotNull();
    }

    @Test
    void noArgConstructor_andSetters_roundTrip() {
        Instant now = Instant.now();
        RestaurantEsDoc d = new RestaurantEsDoc();
        d.setId("x"); d.setTenantId(null); d.setName("N"); d.setCity("C"); d.setAddress("A");
        d.setDescription("D"); d.setPhone("P"); d.setStatus("paused"); d.setIndexedAt(now);
        assertThat(d.getId()).isEqualTo("x");
        assertThat(d.getTenantId()).isNull();
        assertThat(d.getName()).isEqualTo("N");
        assertThat(d.getCity()).isEqualTo("C");
        assertThat(d.getAddress()).isEqualTo("A");
        assertThat(d.getDescription()).isEqualTo("D");
        assertThat(d.getPhone()).isEqualTo("P");
        assertThat(d.getStatus()).isEqualTo("paused");
        assertThat(d.getIndexedAt()).isEqualTo(now);
    }
}
