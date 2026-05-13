package com.onesley.oneclick.core.ai.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.core.ai.api.AiDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Streaming SSE client Groq — Sprint C C.1.
 *
 * <p>Délégue à {@link GroqClient} en stub mode si key absente, sinon ouvre une
 * connexion HTTP streaming + parse SSE Groq + relay vers {@link SseEmitter} client.
 *
 * <p>Format Groq streaming :
 * <pre>
 * data: {"choices":[{"delta":{"content":"Bonjour"}}]}
 * data: {"choices":[{"delta":{"content":" Hicham"}}]}
 * data: [DONE]
 * </pre>
 *
 * <p>Pattern OpenAI-compatible — le frontend legacy parse déjà ce format.
 */
@Component
public class GroqStreamingClient {

    private static final Logger log = LoggerFactory.getLogger(GroqStreamingClient.class);
    private static final java.util.concurrent.ExecutorService STREAM_EXECUTOR =
        Executors.newCachedThreadPool();

    @Value("${app.ai.groq.api-key:}")
    private String apiKey;

    @Value("${app.ai.groq.endpoint:https://api.groq.com/openai/v1/chat/completions}")
    private String endpoint;

    @Value("${app.ai.groq.model:llama-3.3-70b-versatile}")
    private String defaultModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Stream une réponse Groq vers un SseEmitter. Non-bloquant, exécution async.
     *
     * @param emitter         SseEmitter ouvert (timeout 5min default)
     * @param systemPrompt    Message system (optionnel)
     * @param messages        Conversation
     * @param maxTokens       Cap tokens (default 1024)
     */
    public void streamChat(
        SseEmitter emitter,
        String systemPrompt,
        List<AiDtos.ChatMessage> messages,
        Integer maxTokens
    ) {
        CompletableFuture.runAsync(() -> doStream(emitter, systemPrompt, messages, maxTokens), STREAM_EXECUTOR);
    }

    private void doStream(
        SseEmitter emitter,
        String systemPrompt,
        List<AiDtos.ChatMessage> messages,
        Integer maxTokens
    ) {
        try {
            if (apiKey == null || apiKey.isBlank()) {
                // Stub mode : envoie 1 chunk factice puis [DONE]
                log.warn("[ai/groq/stream] api-key absent — stub stream");
                emitter.send(SseEmitter.event()
                    .data("{\"choices\":[{\"delta\":{\"content\":\"[AI not configured — set GROQ_API_KEY to enable streaming]\"}}]}")
                    .build());
                emitter.send(SseEmitter.event().data("[DONE]").build());
                emitter.complete();
                return;
            }

            // Build messages array
            var chatMessages = new java.util.ArrayList<Map<String, String>>();
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
                "temperature", 0.7,
                "stream", true   // ← clé du streaming Groq
            );

            // Connexion HTTP streaming
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(endpoint))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", "text/event-stream")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(body)
                ))
                .build();

            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

            java.net.http.HttpResponse<java.io.InputStream> response =
                httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                log.error("[ai/groq/stream] HTTP {} from Groq", response.statusCode());
                emitter.send(SseEmitter.event().data("[DONE]").build());
                emitter.completeWithError(new RuntimeException("Groq returned " + response.statusCode()));
                return;
            }

            // Parse SSE Groq + relay vers emitter client
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith(":")) continue;
                    if (!line.startsWith("data: ")) continue;
                    String dataStr = line.substring(6).trim();
                    if ("[DONE]".equals(dataStr)) {
                        emitter.send(SseEmitter.event().data("[DONE]").build());
                        break;
                    }
                    // Relay le chunk tel quel au client (qui parse aussi le format Groq)
                    emitter.send(SseEmitter.event().data(dataStr).build());
                }
            }
            emitter.complete();

        } catch (Exception e) {
            log.error("[ai/groq/stream] failed: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().data("[DONE]").build());
            } catch (Exception ignored) { }
            emitter.completeWithError(e);
        }
    }
}
