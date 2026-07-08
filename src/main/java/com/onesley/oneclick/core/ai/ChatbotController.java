package com.onesley.oneclick.core.ai;

import com.onesley.oneclick.core.ai.api.AiOrchestrator;
import com.onesley.oneclick.core.ai.api.ChatbotRequest;
import com.onesley.oneclick.core.ai.api.ChatbotResponse;
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
 * Point d'entrée REST du <b>chatbot</b> : {@code POST /api/ai/chat}.
 *
 * <p><b>Contrat d'architecture</b> : ce contrôleur ne dépend que du port {@link AiOrchestrator}. Toute
 * la décision (outils métier, RAG, mémoire — une ou plusieurs sources) est portée par l'orchestrateur ;
 * le contrôleur se limite au mapping DTO ↔ domaine.
 *
 * <p>À distinguer de {@code /api/ai/ollama/chat} (contrôleur de diagnostic bas niveau, appel LLM direct).
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI — Chatbot", description = "Point d'entrée unique du chatbot (orchestration outils / RAG / mémoire)")
@RequiredArgsConstructor
public class ChatbotController {

    private final AiOrchestrator orchestrator;

    @PostMapping("/chat")
    @Operation(summary = "Pose une question au chatbot ; l'orchestrateur choisit les sources de contexte")
    @PreAuthorize("isAuthenticated()")
    public ChatbotResponse chat(@Valid @RequestBody ChatbotRequest req) {
        return ChatbotResponse.from(orchestrator.answer(req.toQuery()));
    }
}
