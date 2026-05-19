package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.AiDtos;
import com.onesley.oneclick.core.ai.api.AiDtos.AiResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Client HTTP Groq LLM (format OpenAI-compatible) — Sprint B.1 G.4-bis.
 *
 * <p>Endpoint : {@code https://api.groq.com/openai/v1/chat/completions}
 *
 * <p>Stub mode activé automatiquement quand {@code app.ai.groq.api-key} absent.
 * Permet de booter l'app en local sans clé + retourne réponse factice prévisible.
 *
 * <p>Configuration externalisée :
 * <pre>
 * app:
 *   ai:
 *     groq:
 *       api-key: ${GROQ_API_KEY:}
 *       endpoint: ${GROQ_ENDPOINT:https://api.groq.com/openai/v1/chat/completions}
 *       model: ${GROQ_MODEL:llama-3.3-70b-versatile}
 *       timeout-seconds: 30
 * </pre>
 */
@Component
@Slf4j
public class GroqClient {

    @Value("${app.ai.groq.api-key:}")
    private String apiKey;

    @Value("${app.ai.groq.endpoint:https://api.groq.com/openai/v1/chat/completions}")
    private String endpoint;

    @Value("${app.ai.groq.model:llama-3.3-70b-versatile}")
    private String defaultModel;

    private final RestClient restClient = RestClient.builder().build();

    /**
     * Appelle Groq avec une liste de messages (format OpenAI chat completion).
     *
     * @param systemPrompt    Message {@code system} décrivant le rôle (optionnel, null = skip)
     * @param messages        Conversation (rôles user/assistant alternés)
     * @param maxTokens       Cap tokens de réponse (default 1024 si null)
     * @param hint            Texte de fallback en stub mode (ex: "support chat")
     * @return                {@link AiResponseDto} avec message + token counts
     */
    public AiResponseDto chat(
        String systemPrompt,
        List<AiDtos.ChatMessage> messages,
        Integer maxTokens,
        String hint
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[ai/groq] api-key non configurée — stub mode. Hint: {}", hint);
            return AiResponseDto.stub(hint);
        }

        try {
            // 1. Build messages array (system prompt en premier si présent)
            java.util.List<Map<String, String>> chatMessages = new java.util.ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                chatMessages.add(Map.of("role", "system", "content", systemPrompt));
            }
            for (AiDtos.ChatMessage m : messages) {
                chatMessages.add(Map.of("role", m.role(), "content", m.content()));
            }

            Map<String, Object> body = Map.of(
                "model", defaultModel,
                "messages", chatMessages,
                "max_tokens", maxTokens != null ? maxTokens : 1024,
                "temperature", 0.7
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                .uri(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("[ai/groq] HTTP {} from {} — body: {}",
                        res.getStatusCode(), endpoint,
                        new String(res.getBody().readAllBytes()));
                    throw new RestClientException("Groq API returned " + res.getStatusCode());
                })
                .body(Map.class);

            return parseResponse(response, hint);

        } catch (Exception e) {
            log.error("[ai/groq] call failed (fallback stub): {}", e.getMessage(), e);
            return AiResponseDto.stub("error — " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private AiResponseDto parseResponse(Map<String, Object> response, String hint) {
        if (response == null) return AiResponseDto.stub(hint);

        // Format OpenAI : {choices: [{message: {content: "..."}}], usage: {prompt_tokens, completion_tokens}}
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) return AiResponseDto.stub(hint);

        Map<String, Object> firstChoice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
        String content = message != null ? String.valueOf(message.get("content")) : "";

        Map<String, Object> usage = (Map<String, Object>) response.getOrDefault("usage", Map.of());
        int promptTokens = ((Number) usage.getOrDefault("prompt_tokens", 0)).intValue();
        int completionTokens = ((Number) usage.getOrDefault("completion_tokens", 0)).intValue();
        String model = String.valueOf(response.getOrDefault("model", defaultModel));

        return new AiResponseDto(content, model, promptTokens, completionTokens, false);
    }
}
