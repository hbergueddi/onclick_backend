package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.AiDtos;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Test unitaire de {@link GroqStreamingClient} — branche stub (sans clé) via {@code doStream}
 * synchrone (réflexion), pour éviter l'asynchronisme de {@code streamChat}. L'appel SSE Groq
 * réel relève d'un test d'intégration externe.
 */
class GroqStreamingClientTest {

    @Test
    void doStream_noKey_emitsStubAndCompletes() throws Exception {
        GroqStreamingClient client = new GroqStreamingClient(); // apiKey défaut "" → stub stream
        SseEmitter emitter = new SseEmitter();
        Method m = GroqStreamingClient.class.getDeclaredMethod(
            "doStream", SseEmitter.class, String.class, List.class, Integer.class);
        m.setAccessible(true);
        // ne doit pas lever : émet l'événement stub + [DONE] + complete()
        m.invoke(client, emitter, "system", List.of(new AiDtos.ChatMessage("user", "salut")), 100);
    }
}
