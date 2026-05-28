package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.modules.restaurant.api.RestaurantPatchDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires isolés (sans Spring, sans mock) de la logique pure de mapping
 * statut→événement de cycle de vie et de détection de modification de fiche
 * ({@link RestaurantCatalogService#statusEvent(String)} /
 * {@link RestaurantCatalogService#hasEditableFieldChange(RestaurantPatchDto)}).
 *
 * <p>Garantit que les clés produites correspondent exactement à {@code eventConfig}
 * du front (CycleDeVie.tsx) — y compris l'accent de « réactivation ».
 */
class RestaurantLifecycleMappingTest {

    private static RestaurantPatchDto patch(String name, String status) {
        return new RestaurantPatchDto(name, null, null, null, null, null, null,
            status, null, null, null, null, null, null, null);
    }

    @Test
    void statusEvent_mapsCanonicalEnStatesToFrEventKeys() {
        assertThat(RestaurantCatalogService.statusEvent("paused")).isEqualTo("suspension");
        assertThat(RestaurantCatalogService.statusEvent("active")).isEqualTo("réactivation");
        assertThat(RestaurantCatalogService.statusEvent("archived")).isEqualTo("rejet");
    }

    @Test
    void statusEvent_unknownOrNull_fallsBackToModification() {
        assertThat(RestaurantCatalogService.statusEvent("inconnu")).isEqualTo("modification");
        assertThat(RestaurantCatalogService.statusEvent(null)).isEqualTo("modification");
    }

    @Test
    void hasEditableFieldChange_trueWhenAnyNonStatusFieldPresent() {
        assertThat(RestaurantCatalogService.hasEditableFieldChange(patch("Nouveau nom", null))).isTrue();
    }

    @Test
    void hasEditableFieldChange_falseWhenOnlyStatusOrEmpty() {
        assertThat(RestaurantCatalogService.hasEditableFieldChange(patch(null, "active"))).isFalse();
        assertThat(RestaurantCatalogService.hasEditableFieldChange(patch(null, null))).isFalse();
    }
}
