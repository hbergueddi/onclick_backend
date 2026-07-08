package com.onesley.oneclick.core.ai.api;

import java.util.Map;

/**
 * Fragment de contexte neutre agrégé par l'{@link AiOrchestrator} — domaine {@code core/ai}.
 *
 * <p>Unité commune produite par n'importe quelle {@link ContextSource} (RAG, mémoire de conversation,
 * futures sources) : le pipeline de prompting ({@link PromptBuilder}) ne manipule que ce type, sans
 * savoir d'où vient le contexte. Un fragment RAG portera un {@code score} de similarité ; un fragment
 * de mémoire n'en aura pas ({@code null}).
 *
 * @param text     texte du fragment injecté dans le prompt
 * @param metadata métadonnées (source, page, rôle du message…) — jamais nul
 * @param score    pertinence dans {@code [0..1]} si applicable, sinon {@code null}
 */
public record ContextFragment(String text, Map<String, Object> metadata, Double score) {

    public ContextFragment {
        metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
    }

    public static ContextFragment of(String text) {
        return new ContextFragment(text, Map.of(), null);
    }
}
