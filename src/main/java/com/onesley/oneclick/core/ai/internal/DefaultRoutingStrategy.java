package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.AiQuery;
import com.onesley.oneclick.core.ai.api.RoutingDecision;
import com.onesley.oneclick.core.ai.api.RoutingStrategy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Stratégie de routage par défaut (règles simples) — module {@code core/ai}.
 *
 * <p>Comportement :
 * <ul>
 *   <li>si la requête impose des sources → on retient leur intersection avec les sources disponibles ;</li>
 *   <li>sinon (AUTO) → toutes les sources disponibles sont activées ;</li>
 *   <li>Tool Calling activé sauf si la requête le désactive explicitement.</li>
 * </ul>
 *
 * <p>Remplaçable par une stratégie plus fine (classification d'intention, routage piloté par le LLM)
 * en fournissant un autre bean {@link RoutingStrategy} {@code @Primary} — sans toucher à l'orchestrateur.
 */
@Component
class DefaultRoutingStrategy implements RoutingStrategy {

    @Override
    public RoutingDecision decide(AiQuery query, List<String> availableSources) {
        Set<String> selected = new LinkedHashSet<>();
        if (query.sources().isEmpty()) {
            selected.addAll(availableSources);                 // AUTO : tout ce qui est dispo
        } else {
            for (String s : availableSources) {                // sources imposées ∩ disponibles
                if (query.sources().contains(s)) selected.add(s);
            }
        }
        boolean useTools = (query.useTools() == null) ? true : query.useTools();
        return new RoutingDecision(selected, useTools);
    }
}
