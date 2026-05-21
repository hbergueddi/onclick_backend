package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.AiDtos;
import com.onesley.oneclick.core.ai.api.AiDtos.AiResponseDto;
import com.onesley.oneclick.core.ai.api.AiDtos.ChatMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link AiService} (L3 — core.ai). GroqClient mocké :
 * on vérifie la construction des prompts/messages + branches contexte/historique/
 * préférences null vs fournis, et la délégation à {@link GroqClient#chat}.
 */
@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock GroqClient groq;
    @InjectMocks AiService service;

    @Captor ArgumentCaptor<String> systemPrompt;
    @Captor ArgumentCaptor<List<ChatMessage>> messages;
    @Captor ArgumentCaptor<Integer> maxTokens;

    private static final AiResponseDto STUB =
        new AiResponseDto("réponse", "llama", 10, 20, false);

    private void stubChat() {
        when(groq.chat(anyString(), any(), anyInt(), anyString())).thenReturn(STUB);
    }

    // ─── careChat ────────────────────────────────────────────────────────────

    @Test
    void careChat_withContextAndHistory_appendsUserMessage() {
        stubChat();
        var req = new AiDtos.CareChatRequestDto(
            "bonjour j'ai un souci",
            Map.of("firstName", "Ada", "openTickets", 2),
            List.of(new ChatMessage("user", "salut"), new ChatMessage("assistant", "bonjour")));

        AiResponseDto r = service.careChat(req);

        assertThat(r).isSameAs(STUB);
        verify(groq).chat(systemPrompt.capture(), messages.capture(), maxTokens.capture(), anyString());
        assertThat(systemPrompt.getValue()).contains("firstName"); // contexte injecté
        assertThat(messages.getValue()).hasSize(3);                // 2 history + nouveau user
        assertThat(messages.getValue().get(2).content()).isEqualTo("bonjour j'ai un souci");
        assertThat(maxTokens.getValue()).isEqualTo(512);
    }

    @Test
    void careChat_nullContextAndHistory_usesAnonyme_singleMessage() {
        stubChat();
        var req = new AiDtos.CareChatRequestDto("question", null, null);

        service.careChat(req);

        verify(groq).chat(systemPrompt.capture(), messages.capture(), anyInt(), anyString());
        assertThat(systemPrompt.getValue()).contains("anonyme");
        assertThat(messages.getValue()).hasSize(1);
    }

    // ─── assistant ─────────────────────────────────────────────────────────────

    @Test
    void assistant_withContext_injectsContext_maxTokens768() {
        stubChat();
        var req = new AiDtos.AssistantRequestDto("trouve un resto", Map.of("tier", "Ruby"));

        AiResponseDto r = service.assistant(req);

        assertThat(r).isSameAs(STUB);
        verify(groq).chat(systemPrompt.capture(), messages.capture(), maxTokens.capture(), anyString());
        assertThat(systemPrompt.getValue()).contains("Ruby");
        assertThat(messages.getValue()).hasSize(1);
        assertThat(maxTokens.getValue()).isEqualTo(768);
    }

    @Test
    void assistant_nullContext_usesNonFourni() {
        stubChat();
        service.assistant(new AiDtos.AssistantRequestDto("prompt", null));
        verify(groq).chat(systemPrompt.capture(), any(), anyInt(), anyString());
        assertThat(systemPrompt.getValue()).contains("non fourni");
    }

    // ─── eliteReview ───────────────────────────────────────────────────────────

    @Test
    void eliteReview_buildsReviewsText() {
        stubChat();
        var req = new AiDtos.EliteReviewRequestDto("resto-1", List.of(
            new AiDtos.EliteReviewRequestDto.ReviewInput("excellent service", 5),
            new AiDtos.EliteReviewRequestDto.ReviewInput("trop lent", 2)));

        AiResponseDto r = service.eliteReview(req);

        assertThat(r).isSameAs(STUB);
        verify(groq).chat(anyString(), messages.capture(), eq(512), anyString());
        String user = messages.getValue().get(0).content();
        assertThat(user).contains("[5/5] excellent service").contains("[2/5] trop lent");
    }

    // ─── plan ────────────────────────────────────────────────────────────────

    @Test
    void plan_withPreferences_includesThemInPrompt() {
        stubChat();
        var req = new AiDtos.PlanRequestDto("resto-1", "2026-06-01", Map.of("brunch", true));

        service.plan(req);

        verify(groq).chat(anyString(), messages.capture(), eq(512), anyString());
        String user = messages.getValue().get(0).content();
        assertThat(user).contains("resto-1").contains("2026-06-01").contains("brunch");
    }

    @Test
    void plan_nullPreferences_usesStandard() {
        stubChat();
        service.plan(new AiDtos.PlanRequestDto("resto-1", "2026-06-01", null));
        verify(groq).chat(anyString(), messages.capture(), anyInt(), anyString());
        assertThat(messages.getValue().get(0).content()).contains("standard");
    }
}
