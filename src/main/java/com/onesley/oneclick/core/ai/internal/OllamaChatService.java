package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.AiChatApi;
import com.onesley.oneclick.exception.ApiException;
import com.onesley.oneclick.exception.BadRequestException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;

/**
 * Implémentation Ollama du port {@link AiChatApi} (module {@code core/ai}).
 *
 * <p>Fin adaptateur au-dessus du {@link ChatModel} LangChain4j câblé par {@link OllamaConfig} :
 * valide l'entrée, mesure l'appel, traduit les erreurs provider en exceptions HTTP applicatives,
 * et garantit une réponse non vide. La logique métier (system prompts, orchestration, RAG) restera
 * dans des services dédiés — pas ici.
 *
 * <h3>Observabilité</h3>
 * <p>Logs techniques uniquement : modèle, durée, tailles (nombre de caractères) et type d'erreur.
 * <b>Le contenu des prompts et des réponses n'est jamais journalisé</b> (confidentialité).
 */
@Service
@RequiredArgsConstructor
@Slf4j
class OllamaChatService implements AiChatApi {

    private final ChatModel chatModel;
    private final OllamaProperties props;

    @Override
    public Result chat(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new BadRequestException("Le prompt ne peut pas être vide");
        }
        final String model = props.chatModel();
        final long start = System.nanoTime();

        String content;
        try {
            content = chatModel.chat(prompt);
        } catch (RuntimeException e) {
            throw mapAndLog(e, model, elapsedMs(start), prompt.length());
        }

        final long ms = elapsedMs(start);
        if (content == null || content.isBlank()) {
            log.warn("[core/ai] ollama chat empty — model={} durationMs={} promptChars={}",
                model, ms, prompt.length());
            throw new AiUpstreamException("Le modèle a renvoyé une réponse vide");
        }

        log.info("[core/ai] ollama chat ok — model={} durationMs={} promptChars={} respChars={}",
            model, ms, prompt.length(), content.length());
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
        log.warn("[core/ai] ollama chat failed — model={} durationMs={} promptChars={} -> HTTP {} ({}: {})",
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
