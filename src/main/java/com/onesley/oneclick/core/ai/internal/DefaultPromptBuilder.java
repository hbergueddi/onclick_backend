package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.ContextFragment;
import com.onesley.oneclick.core.ai.api.PromptBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Constructeur de prompt par défaut — module {@code core/ai}.
 *
 * <p>Assemble un prompt minimal : sans contexte, renvoie la question telle quelle ; avec contexte,
 * préfixe les fragments et demande au modèle de s'appuyer dessus. Volontairement simple à ce stade —
 * la stratégie de prompting RAG définitive (citations, garde-fous, format) sera introduite avec le
 * pipeline RAG, en remplaçant ce bean par un {@link PromptBuilder} {@code @Primary}.
 */
@Component
class DefaultPromptBuilder implements PromptBuilder {

    @Override
    public String build(String question, List<ContextFragment> context) {
        if (context == null || context.isEmpty()) {
            return question;
        }
        String contextBlock = context.stream()
            .map(ContextFragment::text)
            .collect(Collectors.joining("\n---\n"));
        return """
            Tu réponds à une question en t'appuyant sur le CONTEXTE ci-dessous quand il est pertinent.
            Si le contexte ne suffit pas, réponds avec tes connaissances et signale-le.

            CONTEXTE :
            %s

            QUESTION : %s
            """.formatted(contextBlock, question);
    }
}
