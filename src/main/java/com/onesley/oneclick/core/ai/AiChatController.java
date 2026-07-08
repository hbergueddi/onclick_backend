package com.onesley.oneclick.core.ai;

import com.onesley.oneclick.core.ai.api.AiChatApi;
import com.onesley.oneclick.core.ai.api.ChatRequest;
import com.onesley.oneclick.core.ai.api.ChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur de <b>test / validation</b> de la chaîne LLM :
 * {@code Client → REST → Service → Ollama → Réponse}.
 *
 * <p>Endpoint : {@code POST /api/ai/ollama/chat}.
 *
 * <p><b>Neutralité provider</b> : ce contrôleur ne dépend que du port {@link AiChatApi} (jamais des
 * classes Ollama/LangChain4j). Remplacer Ollama par OpenAI / Azure OpenAI / Groq se fait en changeant
 * l'implémentation du port — ce contrôleur et les DTOs restent inchangés.
 *
 * <p>La gestion d'erreurs (503 provider injoignable, 504 timeout, 502 réponse invalide/vide) est
 * portée par le service + {@code GlobalExceptionHandler} (réponses RFC 7807).
 */
@RestController
@RequestMapping("/api/ai/ollama")
@Tag(name = "AI — Ollama", description = "Validation de bout en bout de la chaîne LLM (Ollama via LangChain4j)")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatApi chat;

    @PostMapping("/chat")
    @Operation(summary = "Envoie un prompt au LLM et retourne la réponse (test de la chaîne complète)")
    @PreAuthorize("isAuthenticated()")
    public ChatResponse chat(@Valid @RequestBody ChatRequest req) {
        return ChatResponse.from(chat.chat(req.prompt()));
    }
}
