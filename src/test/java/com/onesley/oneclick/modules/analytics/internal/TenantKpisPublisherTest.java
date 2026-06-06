package com.onesley.oneclick.modules.analytics.internal;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires isolés du parsing de topic de {@link TenantKpisPublisher} (C3b).
 *
 * <p>La sécurité (abonnement SUPERADMIN) + le push sont couverts par le test WS d'intégration ;
 * ici on valide l'extraction du tenantId du chemin {@code /topic/admin/tenant-kpis/{uuid}}.</p>
 */
class TenantKpisPublisherTest {

    @Test
    void parseTenantId_validUuid() {
        UUID id = UUID.randomUUID();
        assertThat(TenantKpisPublisher.parseTenantId("/topic/admin/tenant-kpis/" + id)).isEqualTo(id);
    }

    @Test
    void topicFor_roundTrips_withParseTenantId() {
        UUID id = UUID.randomUUID();
        assertThat(TenantKpisPublisher.parseTenantId(TenantKpisPublisher.topicFor(id))).isEqualTo(id);
    }

    @Test
    void parseTenantId_wrongPrefix_null() {
        assertThat(TenantKpisPublisher.parseTenantId("/topic/admin/exec")).isNull();
        assertThat(TenantKpisPublisher.parseTenantId("/topic/admin/cross-tenant")).isNull();
    }

    @Test
    void parseTenantId_badUuid_null() {
        assertThat(TenantKpisPublisher.parseTenantId("/topic/admin/tenant-kpis/not-a-uuid")).isNull();
    }

    @Test
    void parseTenantId_nestedOrEmpty_null() {
        assertThat(TenantKpisPublisher.parseTenantId("/topic/admin/tenant-kpis/")).isNull();
        assertThat(TenantKpisPublisher.parseTenantId("/topic/admin/tenant-kpis/" + UUID.randomUUID() + "/x")).isNull();
        assertThat(TenantKpisPublisher.parseTenantId(null)).isNull();
    }
}
