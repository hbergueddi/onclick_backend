package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.AiDtos;
import com.onesley.oneclick.core.ai.api.AiDtos.AiResponseDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de {@link GroqClient} — branche stub (sans clé) + {@code parseResponse}
 * (logique de mapping de la réponse OpenAI) via réflexion. L'appel HTTP réel à Groq
 * relève d'un test d'intégration externe.
 */
class GroqClientTest {

    private final GroqClient client = new GroqClient(); // apiKey défaut "" → stub

    @Test
    void chat_noKey_returnsStub() {
        AiResponseDto r = client.chat("system prompt",
            List.of(new AiDtos.ChatMessage("user", "bonjour")), 100, "support");
        assertThat(r).isNotNull();
    }

    @Test
    void parseResponse_null_empty_valid() throws Exception {
        Method m = GroqClient.class.getDeclaredMethod("parseResponse", Map.class, String.class);
        m.setAccessible(true);

        assertThat(m.invoke(client, null, "hint")).isNotNull();                       // null → stub
        assertThat(m.invoke(client, Map.of("choices", List.of()), "hint")).isNotNull(); // choices vides → stub

        Map<String, Object> valid = Map.of(
            "choices", List.of(Map.of("message", Map.of("content", "Bonjour L4"))),
            "usage", Map.of("prompt_tokens", 5, "completion_tokens", 3),
            "model", "llama-3.3-70b-versatile");
        AiResponseDto r = (AiResponseDto) m.invoke(client, valid, "hint");
        assertThat(r).isNotNull();
        assertThat(r.toString()).contains("Bonjour L4");
    }
}
