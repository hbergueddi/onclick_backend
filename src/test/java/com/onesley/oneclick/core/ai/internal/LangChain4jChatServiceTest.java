package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.AiChatApi;
import com.onesley.oneclick.exception.BadRequestException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link LangChain4jChatService} — provider-neutre (Ollama/Groq) : succès,
 * réponse vide, et mapping des erreurs provider vers les bons codes HTTP. Le {@link ChatModel} est
 * mocké (pas d'appel réseau réel) ; le nom du modèle vient de {@link AiChatModelName}.
 */
class LangChain4jChatServiceTest {

    private final AiChatModelName modelName = new AiChatModelName("test-model");

    private LangChain4jChatService serviceWith(ChatModel model) {
        return new LangChain4jChatService(model, modelName);
    }

    @Test
    void chat_success_returnsContentModelAndDuration() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat("bonjour")).thenReturn("salut !");

        AiChatApi.Result r = serviceWith(model).chat("bonjour");

        assertThat(r.content()).isEqualTo("salut !");
        assertThat(r.model()).isEqualTo("test-model");
        assertThat(r.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void chat_blankPrompt_400() {
        assertThatThrownBy(() -> serviceWith(mock(ChatModel.class)).chat("  "))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void chat_emptyResponse_502() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat("x")).thenReturn("   ");
        assertThatThrownBy(() -> serviceWith(model).chat("x"))
            .isInstanceOf(AiUpstreamException.class)
            .extracting(e -> ((AiUpstreamException) e).getStatus())
            .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void chat_timeout_504() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat("x")).thenThrow(new TimeoutException("timed out"));
        assertThatThrownBy(() -> serviceWith(model).chat("x"))
            .isInstanceOf(AiTimeoutException.class)
            .extracting(e -> ((AiTimeoutException) e).getStatus())
            .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void chat_serverUnreachable_503() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat("x")).thenThrow(new UnresolvedModelServerException("no server"));
        assertThatThrownBy(() -> serviceWith(model).chat("x"))
            .isInstanceOf(AiUnavailableException.class)
            .extracting(e -> ((AiUnavailableException) e).getStatus())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void chat_connectionRefusedWrapped_503() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat("x")).thenThrow(new RuntimeException("io", new ConnectException("refused")));
        assertThatThrownBy(() -> serviceWith(model).chat("x"))
            .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void chat_genericProviderError_502() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat("x")).thenThrow(new RuntimeException("boom"));
        assertThatThrownBy(() -> serviceWith(model).chat("x"))
            .isInstanceOf(AiUpstreamException.class)
            .extracting(e -> ((AiUpstreamException) e).getStatus())
            .isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
