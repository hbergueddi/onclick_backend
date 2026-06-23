package com.onesley.oneclick.modules.loyalty.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires isolés du {@link LoyaltyTierResolver} (CL-2 — source unique du palier).
 *
 * <p>Couvre les deux chemins : <b>DB d'abord</b> (table {@code tiers} du tenant) et <b>fallback
 * canonique</b> (tenant sans palier configuré OU tenant {@code null}).
 */
class LoyaltyTierResolverTest {

    private final TierRepository tierRepository = mock(TierRepository.class);
    private final LoyaltyTierResolver resolver = new LoyaltyTierResolver(tierRepository);

    private Tier tier(UUID tenant, String name, int min) {
        return new Tier(UUID.randomUUID(), tenant, name, min, new BigDecimal("0.00"));
    }

    // ── tierNameFor : DB d'abord ──────────────────────────────────────────────────
    @Test
    void tierNameFor_dbConfigured_returnsHighestThresholdAtOrBelowPoints() {
        UUID tenant = UUID.randomUUID();
        when(tierRepository.findAllByTenantId(tenant)).thenReturn(List.of(
            tier(tenant, "Bronze", 0), tier(tenant, "Argent", 500), tier(tenant, "Or", 2000)));

        assertThat(resolver.tierNameFor(0, tenant)).isEqualTo("Bronze");
        assertThat(resolver.tierNameFor(499, tenant)).isEqualTo("Bronze");
        assertThat(resolver.tierNameFor(500, tenant)).isEqualTo("Argent");
        assertThat(resolver.tierNameFor(5000, tenant)).isEqualTo("Or");
    }

    // ── tierNameFor : fallback canonique ──────────────────────────────────────────
    @Test
    void tierNameFor_noDbTiers_usesCanonicalThresholds() {
        UUID tenant = UUID.randomUUID();
        when(tierRepository.findAllByTenantId(any())).thenReturn(List.of());

        // Paliers charte (V98) : Connaisseur 200 / Grand Cru 500 / Signature 1000 / Ambassadeur 10000 /
        // Table Secrète 50000. Connaisseur = plancher (renvoyé sous 500).
        assertThat(resolver.tierNameFor(0, tenant)).isEqualTo("Connaisseur");
        assertThat(resolver.tierNameFor(499, tenant)).isEqualTo("Connaisseur");
        assertThat(resolver.tierNameFor(500, tenant)).isEqualTo("Grand Cru");
        assertThat(resolver.tierNameFor(1_000, tenant)).isEqualTo("Signature");
        assertThat(resolver.tierNameFor(10_000, tenant)).isEqualTo("Ambassadeur");
        assertThat(resolver.tierNameFor(50_000, tenant)).isEqualTo("Table Secrète");
    }

    @Test
    void tierNameFor_nullTenant_usesCanonicalFallback_neverNull() {
        assertThat(resolver.tierNameFor(0, null)).isEqualTo("Connaisseur");
        assertThat(resolver.tierNameFor(9_999, null)).isEqualTo("Signature");
    }

    // ── nextTierPoints ────────────────────────────────────────────────────────────
    @Test
    void nextTierPoints_dbConfigured_returnsNextThresholdAbove() {
        UUID tenant = UUID.randomUUID();
        when(tierRepository.findAllByTenantId(tenant)).thenReturn(List.of(
            tier(tenant, "Bronze", 0), tier(tenant, "Argent", 500), tier(tenant, "Or", 2000)));

        assertThat(resolver.nextTierPoints(0, tenant)).isEqualTo(500);
        assertThat(resolver.nextTierPoints(500, tenant)).isEqualTo(2000);
        assertThat(resolver.nextTierPoints(2000, tenant)).isEqualTo(-1); // déjà au sommet DB
    }

    @Test
    void nextTierPoints_noDbTiers_usesCanonical() {
        UUID tenant = UUID.randomUUID();
        when(tierRepository.findAllByTenantId(any())).thenReturn(List.of());

        assertThat(resolver.nextTierPoints(0, tenant)).isEqualTo(500);
        assertThat(resolver.nextTierPoints(500, tenant)).isEqualTo(1_000);
        assertThat(resolver.nextTierPoints(1_000, tenant)).isEqualTo(10_000);
        assertThat(resolver.nextTierPoints(10_000, tenant)).isEqualTo(50_000);
        assertThat(resolver.nextTierPoints(50_000, tenant)).isEqualTo(-1);
    }
}
