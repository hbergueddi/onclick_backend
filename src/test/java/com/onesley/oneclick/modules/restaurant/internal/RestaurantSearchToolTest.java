package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.modules.restaurant.api.RestaurantDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests unitaires de {@link RestaurantSearchTool} — outil chatbot lecture (recherche resto par nom). */
class RestaurantSearchToolTest {

    private final RestaurantCatalogService catalog = mock(RestaurantCatalogService.class);
    private final RestaurantSearchTool tool = new RestaurantSearchTool(catalog);

    private static RestaurantDto resto(String name, String city) {
        return new RestaurantDto(
            UUID.randomUUID(), UUID.randomUUID(), name, null, null, null, city, null, null,
            "active", null, List.of(), 0, null, "marocaine", null, null, null, null, null, null,
            null, null, Instant.now(), null, List.of());
    }

    @Test
    void metadata_isStable() {
        assertThat(tool.name()).isEqualTo("search_restaurants");
        assertThat(tool.parameters()).containsKey("query");
    }

    @Test
    void execute_found_listsWithIds() {
        RestaurantDto r = resto("Le Riad", "Marrakech");
        when(catalog.searchByName(eq("riad"), anyInt())).thenReturn(List.of(r));

        String out = tool.execute(Map.of("query", "riad"));

        assertThat(out).contains("id=" + r.id()).contains("Le Riad").contains("Marrakech");
    }

    @Test
    void execute_blankQuery_asksForName() {
        assertThat(tool.execute(Map.of("query", "  "))).contains("Précise un nom");
    }

    @Test
    void execute_empty_returnsFriendlyMessage() {
        when(catalog.searchByName(eq("inconnu"), anyInt())).thenReturn(List.of());
        assertThat(tool.execute(Map.of("query", "inconnu"))).contains("Aucun restaurant trouvé");
    }
}
