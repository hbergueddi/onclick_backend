package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.AiAnswer;
import com.onesley.oneclick.core.ai.api.AiChatApi;
import com.onesley.oneclick.core.ai.api.AiOrchestrator;
import com.onesley.oneclick.core.ai.api.AiQuery;
import com.onesley.oneclick.core.ai.api.AiTool;
import com.onesley.oneclick.core.ai.api.ContextFragment;
import com.onesley.oneclick.core.ai.api.ContextSource;
import com.onesley.oneclick.core.ai.api.ConversationMemory;
import com.onesley.oneclick.core.ai.api.ConversationMessage;
import com.onesley.oneclick.core.ai.api.PromptBuilder;
import com.onesley.oneclick.core.ai.api.RoutingDecision;
import com.onesley.oneclick.core.ai.api.RoutingStrategy;
import com.onesley.oneclick.core.ai.api.ToolRegistry;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.security.SecurityHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrateur IA — implémentation du point d'entrée {@link AiOrchestrator} (module {@code core/ai}).
 *
 * <p><b>Ne dépend que de ports du domaine</b> ({@link ContextSource}, {@link RoutingStrategy},
 * {@link ToolRegistry}, {@link PromptBuilder}, {@link AiChatApi}) — aucune référence à Ollama,
 * PostgreSQL ou LangChain4j. Le flux :
 * <ol>
 *   <li>{@link RoutingStrategy} décide des sources à activer et de l'usage des outils ;</li>
 *   <li>les {@link ContextSource} sélectionnées fournissent des {@link ContextFragment} (agrégés) ;</li>
 *   <li>les {@link AiTool} sont exposés si la décision l'autorise (exécution câblée ultérieurement) ;</li>
 *   <li>le {@link PromptBuilder} assemble le prompt (question + contexte) ;</li>
 *   <li>{@link AiChatApi} génère la réponse.</li>
 * </ol>
 *
 * <p>Extensible sans modification : de nouvelles {@link ContextSource} et de nouveaux {@link AiTool}
 * (beans Spring) sont automatiquement pris en compte. L'ensemble est chronométré via
 * {@code ai.rag.query.duration}.
 *
 * <p><b>Fondation</b> : ni RAG ni outils réels à ce stade → sources vides + aucun outil ⇒ réponse LLM
 * directe. Le flux d'orchestration est néanmoins complet et fonctionnel.
 */
@Service
@Slf4j
class AiOrchestratorService implements AiOrchestrator {

    private final List<ContextSource> contextSources;
    private final RoutingStrategy routingStrategy;
    private final ToolRegistry toolRegistry;
    private final PromptBuilder promptBuilder;
    private final AiChatApi chatApi;
    private final ConversationMemory memory;
    private final AiMetrics metrics;

    AiOrchestratorService(List<ContextSource> contextSources, RoutingStrategy routingStrategy,
                          ToolRegistry toolRegistry, PromptBuilder promptBuilder,
                          AiChatApi chatApi, ConversationMemory memory, AiMetrics metrics) {
        this.contextSources = List.copyOf(contextSources);
        this.routingStrategy = routingStrategy;
        this.toolRegistry = toolRegistry;
        this.promptBuilder = promptBuilder;
        this.chatApi = chatApi;
        this.memory = memory;
        this.metrics = metrics;
    }

    @Override
    public AiAnswer answer(AiQuery query) {
        if (query == null || query.question() == null || query.question().isBlank()) {
            throw new BadRequestException("La question ne peut pas être vide");
        }
        return metrics.ragQueryTimer().record(() -> orchestrate(query));
    }

    private AiAnswer orchestrate(AiQuery query) {
        List<String> available = contextSources.stream().map(ContextSource::name).toList();
        RoutingDecision decision = routingStrategy.decide(query, available);

        // 1) Agrégation du contexte des sources sélectionnées (RAG, mémoire… à venir).
        List<ContextFragment> context = contextSources.stream()
            .filter(s -> decision.contextSources().contains(s.name()))
            .flatMap(s -> s.retrieve(query).stream())
            .toList();

        // 2) Outils exposés (exécution Tool Calling câblée à l'étape suivante).
        List<AiTool> tools = decision.useTools() ? toolRegistry.all() : List.of();

        // 3) Prompt + génération (avec Tool Calling si des outils sont exposés).
        String prompt = promptBuilder.build(query.question(), context);
        AiChatApi.Result result = tools.isEmpty() ? chatApi.chat(prompt) : chatApi.chat(prompt, tools);

        // 4) Mémoire : on persiste le tour (question + réponse) si une conversation est fournie.
        persistTurn(query, result.content());

        List<String> usedSources = decision.contextSources().stream().toList();
        List<String> toolNames = tools.stream().map(AiTool::name).toList();
        log.info("[core/ai] orchestrated — sources={} tools={} contextFragments={} durationMs={}",
            usedSources, toolNames, context.size(), result.durationMs());

        return new AiAnswer(result.content(), usedSources, toolNames, context.size());
    }

    /** Enregistre le tour courant dans la mémoire de conversation (no-op sans {@code conversationId}). */
    private void persistTurn(AiQuery query, String answer) {
        String conversationId = query.conversationId();
        if (conversationId == null || conversationId.isBlank()) return;
        UUID userId = SecurityHelper.currentUserId();
        memory.append(conversationId, userId, ConversationMessage.ROLE_USER, query.question());
        memory.append(conversationId, userId, ConversationMessage.ROLE_ASSISTANT, answer);
    }
}
