package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.AiAnswer;
import com.onesley.oneclick.core.ai.api.AiChatApi;
import com.onesley.oneclick.core.ai.api.AiQuery;
import com.onesley.oneclick.core.ai.api.AiTool;
import com.onesley.oneclick.core.ai.api.ContextFragment;
import com.onesley.oneclick.core.ai.api.ContextSource;
import com.onesley.oneclick.core.ai.api.PromptBuilder;
import com.onesley.oneclick.core.ai.api.RoutingDecision;
import com.onesley.oneclick.core.ai.api.RoutingStrategy;
import com.onesley.oneclick.core.ai.api.ToolRegistry;
import com.onesley.oneclick.exception.BadRequestException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link AiOrchestratorService} — valide le FLUX d'orchestration avec ports mockés :
 * décision de routage, agrégation mono/multi-sources, filtrage par la décision, exposition des outils,
 * et garde-fou d'entrée. Aucune dépendance à Ollama/PG/LangChain4j (que des ports).
 */
class AiOrchestratorServiceTest {

    private final RoutingStrategy routing = mock(RoutingStrategy.class);
    private final ToolRegistry tools = mock(ToolRegistry.class);
    private final PromptBuilder prompt = mock(PromptBuilder.class);
    private final AiChatApi chat = mock(AiChatApi.class);
    private final AiMetrics metrics = new AiMetrics(new SimpleMeterRegistry());

    private AiOrchestratorService orchestrator(List<ContextSource> sources) {
        return new AiOrchestratorService(sources, routing, tools, prompt, chat, metrics);
    }

    private static ContextSource source(String name, ContextFragment... fragments) {
        ContextSource s = mock(ContextSource.class);
        when(s.name()).thenReturn(name);
        when(s.retrieve(any())).thenReturn(List.of(fragments));
        return s;
    }

    private void stubChat(String reply) {
        when(chat.chat(any())).thenReturn(new AiChatApi.Result(reply, "test-model", 3L));
        when(prompt.build(any(), anyList())).thenReturn("PROMPT");
    }

    @Test
    void answer_noSources_auto_directLlm() {
        stubChat("ANSWER");
        when(routing.decide(any(), eq(List.of()))).thenReturn(new RoutingDecision(Set.of(), false));

        AiAnswer a = orchestrator(List.of()).answer(AiQuery.of("bonjour"));

        assertThat(a.content()).isEqualTo("ANSWER");
        assertThat(a.contextSourcesUsed()).isEmpty();
        assertThat(a.toolsAvailable()).isEmpty();
        assertThat(a.contextFragmentCount()).isZero();
    }

    @Test
    void answer_multiSource_aggregatesContext_andExposesTools() {
        stubChat("A");
        ContextSource rag = source("rag", ContextFragment.of("ragCtx"));
        ContextSource memory = source("memory", ContextFragment.of("memCtx"));
        when(routing.decide(any(), any())).thenReturn(new RoutingDecision(Set.of("rag", "memory"), true));
        AiTool tool = mock(AiTool.class);
        when(tool.name()).thenReturn("getReservation");
        when(tools.all()).thenReturn(List.of(tool));

        AiAnswer a = orchestrator(List.of(rag, memory)).answer(AiQuery.of("q"));

        assertThat(a.contextFragmentCount()).isEqualTo(2);
        assertThat(a.contextSourcesUsed()).containsExactlyInAnyOrder("rag", "memory");
        assertThat(a.toolsAvailable()).containsExactly("getReservation");

        // Le PromptBuilder reçoit bien les 2 fragments agrégés.
        ArgumentCaptor<List<ContextFragment>> cap = ArgumentCaptor.forClass(List.class);
        verify(prompt).build(eq("q"), cap.capture());
        assertThat(cap.getValue()).hasSize(2);
    }

    @Test
    void answer_singleSource_filtersOutUnselected_andToolsOff() {
        stubChat("A");
        ContextSource rag = source("rag", ContextFragment.of("ragCtx"));
        ContextSource memory = source("memory", ContextFragment.of("memCtx"));
        // La décision ne retient que "rag" et coupe les outils.
        when(routing.decide(any(), any())).thenReturn(new RoutingDecision(Set.of("rag"), false));

        AiAnswer a = orchestrator(List.of(rag, memory)).answer(AiQuery.of("q"));

        assertThat(a.contextFragmentCount()).isEqualTo(1);
        assertThat(a.contextSourcesUsed()).containsExactly("rag");
        assertThat(a.toolsAvailable()).isEmpty();
        verify(memory, never()).retrieve(any()); // source non sélectionnée jamais interrogée
        verify(tools, never()).all();            // outils coupés → registre non consulté
    }

    @Test
    void answer_blankQuestion_throws400_beforeAnyCall() {
        assertThatThrownBy(() -> orchestrator(List.of()).answer(AiQuery.of("   ")))
            .isInstanceOf(BadRequestException.class);
        verify(chat, never()).chat(any());
        verify(routing, never()).decide(any(), any());
    }
}
