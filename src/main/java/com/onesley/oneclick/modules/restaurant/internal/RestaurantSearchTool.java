package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.core.ai.api.AiTool;
import com.onesley.oneclick.modules.restaurant.api.RestaurantDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Outil métier <b>lecture seule</b> exposé au chatbot : recherche de restaurants par nom.
 *
 * <p>Vit dans {@code modules/restaurant} (propriétaire de la donnée) et implémente le port
 * {@link AiTool} de {@code core.ai.api}. Renvoie l'{@code id} de chaque restaurant : le modèle s'en
 * sert ensuite pour l'outil de réservation ({@code book_reservation}).
 *
 * <p><b>Sécurité</b> : la recherche est bornée au périmètre visible du client
 * ({@code TenantScope} dans {@code RestaurantCatalogService#searchByName}) — impossible de découvrir
 * des restaurants d'un programme auquel l'utilisateur n'a pas accès.
 */
@Component
@RequiredArgsConstructor
public class RestaurantSearchTool implements AiTool {

    private static final int MAX_RESULTS = 8;

    private final RestaurantCatalogService catalogService;

    @Override
    public String name() {
        return "search_restaurants";
    }

    @Override
    public String description() {
        return "Recherche des restaurants par nom (ou mot-clé). Retourne pour chacun un identifiant (id) "
            + "à réutiliser tel quel pour réserver via l'outil book_reservation.";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of("query", "Nom ou mot-clé du restaurant recherché");
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        Object q = arguments.get("query");
        String query = (q == null) ? "" : String.valueOf(q).trim();
        if (query.isBlank()) {
            return "Précise un nom de restaurant à rechercher.";
        }
        List<RestaurantDto> results = catalogService.searchByName(query, MAX_RESULTS);
        if (results.isEmpty()) {
            return "Aucun restaurant trouvé pour « " + query + " ».";
        }
        StringBuilder sb = new StringBuilder("Restaurants trouvés (" + results.size() + ") :\n");
        for (RestaurantDto r : results) {
            sb.append("- id=").append(r.id())
                .append(" | ").append(r.name() != null ? r.name() : "?")
                .append(r.city() != null ? " — " + r.city() : "")
                .append(r.cuisine() != null ? " (" + r.cuisine() + ")" : "")
                .append('\n');
        }
        return sb.toString();
    }
}
