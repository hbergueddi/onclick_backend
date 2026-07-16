package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.ContextFragment;
import com.onesley.oneclick.core.ai.api.PromptBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Constructeur de prompt par défaut — module {@code core/ai}.
 *
 * <p><b>Garde-fou « données OneClick uniquement »</b> : le prompt contraint le modèle à répondre
 * exclusivement à partir des données mises à sa disposition — le CONTEXTE (RAG pgvector + mémoire) et
 * les résultats des outils (recherche resto, réservations), qui proviennent tous de la base OneClick.
 * Il lui interdit explicitement d'utiliser sa connaissance générale ou toute source externe, et lui
 * demande de signaler l'absence d'information plutôt que d'inventer. Le cadre est appliqué même sans
 * contexte (la question n'est jamais renvoyée « nue » au LLM), pour couvrir le cas Tool Calling.
 */
@Component
class DefaultPromptBuilder implements PromptBuilder {

    @Override
    public String build(String question, List<ContextFragment> context) {
        String contextBlock = (context == null || context.isEmpty())
            ? "(aucun élément de contexte)"
            : context.stream()
                .map(ContextFragment::text)
                .collect(Collectors.joining("\n---\n"));
        return """
            Tu es l'assistant OneClick. Tu réponds UNIQUEMENT à partir des données OneClick mises à ta \
            disposition : le CONTEXTE ci-dessous et les résultats des outils que tu appelles (recherche \
            de restaurants, réservations…). Ces données proviennent toutes de la base de données OneClick.

            Règles STRICTES :
            - N'utilise JAMAIS tes connaissances générales ni aucune source externe.
            - N'invente aucune donnée (restaurant, horaire, réservation, prix, disponibilité…).
            - Si l'information n'est ni dans le CONTEXTE ni obtenable via un outil, réponds explicitement \
              que tu ne disposes pas de cette information dans OneClick — ne devine pas.
            - Réponds en français, de façon concise.

            CONTEXTE :
            %s

            QUESTION : %s
            """.formatted(contextBlock, question);
    }
}
