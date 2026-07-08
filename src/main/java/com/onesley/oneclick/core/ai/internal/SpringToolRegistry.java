package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.AiTool;
import com.onesley.oneclick.core.ai.api.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Catalogue d'outils adossé au contexte Spring — module {@code core/ai}.
 *
 * <p>Agrège tous les beans {@link AiTool} présents (injection de {@code List<AiTool>}). Ajouter un
 * outil métier = déclarer une nouvelle implémentation {@code @Component} : elle est automatiquement
 * exposée, sans modifier l'orchestrateur ni le registre.
 *
 * <p><b>Fondation</b> : aucun {@link AiTool} n'existe encore → la liste est vide.
 */
@Component
class SpringToolRegistry implements ToolRegistry {

    private final List<AiTool> tools;

    SpringToolRegistry(List<AiTool> tools) {
        this.tools = List.copyOf(tools);
    }

    @Override
    public List<AiTool> all() {
        return tools;
    }

    @Override
    public Optional<AiTool> byName(String name) {
        return tools.stream().filter(t -> t.name().equals(name)).findFirst();
    }
}
