package com.onesley.oneclick.core.ai.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.core.ai.api.AiChatApi;
import com.onesley.oneclick.core.ai.api.AiTool;
import com.onesley.oneclick.exception.ApiException;
import com.onesley.oneclick.exception.BadRequestException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implémentation LangChain4j du port {@link AiChatApi} (module {@code core/ai}), <b>neutre vis-à-vis du
 * provider</b> : fonctionne avec n'importe quel {@link ChatModel} — Ollama (local) ou Groq/OpenAI
 * (managé) — selon le bean actif ({@code app.ai.provider}). Le nom du modèle vient de
 * {@link AiChatModelName}, découplant le service de la config d'un provider donné.
 *
 * <p>Rôle : valide l'entrée, mesure l'appel, traduit les erreurs provider en exceptions HTTP
 * applicatives, garantit une réponse non vide, et exécute la boucle de Tool Calling. La logique métier
 * (system prompts, orchestration, RAG) reste dans des services dédiés — pas ici.
 *
 * <h3>Tool Calling</h3>
 * <p>{@link #chat(String, List)} convertit les {@link AiTool} du domaine en {@code ToolSpecification}
 * LangChain4j, laisse le modèle demander des appels d'outils, les exécute et boucle (borné par
 * {@link #MAX_TOOL_ITERATIONS}). La mécanique LangChain4j ne fuit jamais hors de cette classe.
 *
 * <h3>Observabilité</h3>
 * <p>Logs techniques uniquement (modèle, durée, tailles, outils appelés) — <b>jamais</b> le contenu des
 * prompts ou des réponses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class LangChain4jChatService implements AiChatApi {

    /** Garde-fou anti-boucle : nombre maximum d'allers-retours d'appels d'outils. */
    private static final int MAX_TOOL_ITERATIONS = 5;

    private final ChatModel chatModel;
    private final AiChatModelName modelName;
    private final ObjectMapper objectMapper;

    @Override
    public Result chat(String prompt) {
        requirePrompt(prompt);
        final String model = modelName.value();
        final long start = System.nanoTime();
        String content;
        try {
            content = chatModel.chat(prompt);
        } catch (RuntimeException e) {
            throw mapAndLog(e, model, elapsedMs(start), prompt.length());
        }
        return finish(content, model, start, prompt.length(), 0);
    }

    @Override
    public Result chat(String prompt, List<AiTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return chat(prompt);
        }
        requirePrompt(prompt);
        final String model = modelName.value();
        final long start = System.nanoTime();

        Map<String, AiTool> byName = new LinkedHashMap<>();
        tools.forEach(t -> byName.put(t.name(), t));
        List<ToolSpecification> specs = tools.stream().map(this::toSpecification).toList();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from(prompt));

        String content = null;
        int toolCalls = 0;
        try {
            for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
                AiMessage ai = chatModel.chat(ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(specs)
                    .build()).aiMessage();
                messages.add(ai);

                if (!ai.hasToolExecutionRequests()) {
                    content = ai.text();
                    break;
                }
                for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
                    toolCalls++;
                    messages.add(ToolExecutionResultMessage.from(req, executeTool(byName, req)));
                }
            }
        } catch (RuntimeException e) {
            throw mapAndLog(e, model, elapsedMs(start), prompt.length());
        }
        return finish(content, model, start, prompt.length(), toolCalls);
    }

    // ─── Tool Calling helpers ─────────────────────────────────────────────────────

    /**
     * Convertit un {@link AiTool} neutre en {@code ToolSpecification} LangChain4j (params = string).
     * Pour un outil <b>sans paramètre</b>, on n'attache PAS de schéma : c'est la forme canonique
     * « function sans arguments » côté OpenAI/Groq (un schéma objet vide y est mal interprété).
     */
    private ToolSpecification toSpecification(AiTool tool) {
        ToolSpecification.Builder builder = ToolSpecification.builder()
            .name(tool.name())
            .description(tool.description());
        if (!tool.parameters().isEmpty()) {
            JsonObjectSchema.Builder schema = JsonObjectSchema.builder();
            tool.parameters().forEach(schema::addStringProperty);
            builder.parameters(schema.build());
        }
        return builder.build();
    }

    /**
     * Exécute l'outil demandé par le modèle. Toute erreur (outil inconnu, arguments illisibles, échec
     * d'exécution) est renvoyée comme <b>texte</b> au modèle plutôt que de casser la conversation.
     */
    private String executeTool(Map<String, AiTool> byName, ToolExecutionRequest req) {
        AiTool tool = byName.get(req.name());
        if (tool == null) {
            log.warn("[core/ai] tool call inconnu name={}", req.name());
            return "Erreur : outil inconnu '" + req.name() + "'.";
        }
        try {
            Map<String, Object> args = parseArguments(req.arguments());
            log.info("[core/ai] tool call name={} argKeys={}", req.name(), args.keySet());
            String result = tool.execute(args);
            return (result == null) ? "" : result;
        } catch (RuntimeException e) {
            log.warn("[core/ai] tool call échec name={} : {}", req.name(), e.getMessage());
            return "Erreur lors de l'exécution de l'outil : " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String json) {
        // Groq/OpenAI peuvent renvoyer null, "" ou le littéral "null" pour un appel sans argument.
        if (json == null || json.isBlank() || "null".equals(json.trim())) return Map.of();
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            return (parsed == null) ? Map.of() : parsed;
        } catch (Exception e) {
            throw new IllegalArgumentException("Arguments d'outil JSON invalides", e);
        }
    }

    // ─── Communs ──────────────────────────────────────────────────────────────────

    private static void requirePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new BadRequestException("Le prompt ne peut pas être vide");
        }
    }

    private Result finish(String content, String model, long start, int promptChars, int toolCalls) {
        final long ms = elapsedMs(start);
        if (content == null || content.isBlank()) {
            log.warn("[core/ai] chat empty — model={} durationMs={} promptChars={} toolCalls={}",
                model, ms, promptChars, toolCalls);
            throw new AiUpstreamException("Le modèle a renvoyé une réponse vide");
        }
        log.info("[core/ai] chat ok — model={} durationMs={} promptChars={} respChars={} toolCalls={}",
            model, ms, promptChars, content.length(), toolCalls);
        return new Result(content, model, ms);
    }

    /**
     * Traduit une exception LangChain4j/JDK en exception HTTP applicative + log technique
     * (sans contenu). 504 = timeout, 503 = provider injoignable, 502 = erreur upstream/autre.
     */
    private ApiException mapAndLog(RuntimeException e, String model, long ms, int promptChars) {
        final ApiException mapped;
        if (e instanceof TimeoutException || hasCause(e, HttpTimeoutException.class)) {
            mapped = new AiTimeoutException("Délai dépassé lors de l'appel au modèle", e);
        } else if (e instanceof UnresolvedModelServerException
            || hasCause(e, ConnectException.class)
            || hasCause(e, UnknownHostException.class)) {
            mapped = new AiUnavailableException("Provider LLM injoignable", e);
        } else {
            mapped = new AiUpstreamException("Erreur du provider LLM", e);
        }
        log.warn("[core/ai] chat failed — model={} durationMs={} promptChars={} -> HTTP {} ({}: {})",
            model, ms, promptChars, mapped.getStatus().value(), e.getClass().getSimpleName(), e.getMessage());
        return mapped;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) return true;
        }
        return false;
    }
}
