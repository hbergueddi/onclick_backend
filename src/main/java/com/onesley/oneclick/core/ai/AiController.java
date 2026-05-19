package com.onesley.oneclick.core.ai;

import com.onesley.oneclick.core.ai.api.AiDtos;
import com.onesley.oneclick.core.ai.api.AiDtos.AiResponseDto;
import com.onesley.oneclick.core.ai.internal.AiService;
import com.onesley.oneclick.core.ai.internal.GroqStreamingClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Contrôleur AI — Sprint B.1 G.4-bis (port des 4 EFs Groq legacy).
 *
 * <p>Endpoints :
 * <ul>
 *   <li>{@code POST /api/ai/care-chat}      — support chat (port EF oneclick-care-chat)</li>
 *   <li>{@code POST /api/ai/assistant}      — Pocket AI (port EF oneclick-ai-assistant)</li>
 *   <li>{@code POST /api/ai/elite-review}   — résumé reviews (port EF elite-ai-review)</li>
 *   <li>{@code POST /api/ai/plan}            — génération plan (port EF generate-plan)</li>
 * </ul>
 *
 * <p>Rate-limit appliqué :
 * <ul>
 *   <li>{@code /api/ai/assistant} : 5 req/jour par user (côté frontend table ai_usage).
 *       En V2 backend : migrer ai_usage → Spring + check côté service.</li>
 *   <li>{@code /api/ai/care-chat} : 30 req/h par IP (cf application.yml rate-limit).</li>
 *   <li>{@code /api/ai/elite-review} : 10 req/h par IP.</li>
 *   <li>{@code /api/ai/plan} : 5 req/h par IP (coûteux Groq).</li>
 * </ul>
 *
 * <p>Si {@code GROQ_API_KEY} absente → stub mode (cf {@link com.onesley.oneclick.core.ai.internal.GroqClient}).
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI", description = "Wrappers Groq LLM — Sprint B.1 G.4-bis (port 4 EFs Supabase)")
public class AiController {

    private final AiService service;
    private final GroqStreamingClient streamingClient;

    public AiController(AiService service, GroqStreamingClient streamingClient) {
        this.service = service;
        this.streamingClient = streamingClient;
    }

    @PostMapping("/care-chat")
    @Operation(summary = "Support chat — répond aux questions client avec contexte tickets")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public AiResponseDto careChat(@Valid @RequestBody AiDtos.CareChatRequestDto req) {
        return service.careChat(req);
    }

    @PostMapping("/assistant")
    @Operation(summary = "Assistant Pocket — répond aux prompts user (rate-limit 5/jour côté frontend)")
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public AiResponseDto assistant(@Valid @RequestBody AiDtos.AssistantRequestDto req) {
        return service.assistant(req);
    }

    @PostMapping("/elite-review")
    @Operation(summary = "Résumé reviews — analyse N avis client d'un restaurant")
    @PreAuthorize("hasAuthority('CREATE:LOYALTY')")
    public AiResponseDto eliteReview(@Valid @RequestBody AiDtos.EliteReviewRequestDto req) {
        return service.eliteReview(req);
    }

    @PostMapping("/plan")
    @Operation(summary = "Génération plan hebdomadaire — planning service auto")
    @PreAuthorize("hasAuthority('CREATE:LOYALTY')")
    public AiResponseDto plan(@Valid @RequestBody AiDtos.PlanRequestDto req) {
        return service.plan(req);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Streaming SSE endpoints (Sprint C C.1)
    // ═══════════════════════════════════════════════════════════════════════
    // Le frontend legacy (useCareChat + useAIAssistant) parse déjà le format
    // OpenAI-compatible streaming. Notre GroqStreamingClient relaie les chunks
    // tel quel pour zero refacto côté frontend.

    @PostMapping(value = "/care-chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "Sprint C — Support chat streaming SSE (port EF oneclick-care-chat)",
        description = "Stream Groq chunks tel quel — format OpenAI-compatible (data: {choices: [...]} + [DONE])"
    )
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public SseEmitter careChatStream(@Valid @RequestBody AiDtos.CareChatRequestDto req) {
        // Timeout 5 min — large pour permettre les longs streams
        SseEmitter emitter = new SseEmitter(300_000L);

        String systemPrompt = """
            Tu es un assistant support OneClick — plateforme de fidélisation restaurants au Maroc.
            Réponds en français, ton chaleureux mais concis. Maximum 3 phrases courtes.
            Si tu ne sais pas, dis-le honnêtement et propose de contacter l'équipe humaine.
            Contexte client : %s

            Tu peux émettre des blocs d'action si pertinent :
              [CREATE_TICKET]category:..|priority:..|subject:..[/CREATE_TICKET]
              [CREATE_RESERVATION]restaurant_id:..|date:..|heure:..|couverts:..[/CREATE_RESERVATION]
              [CREATE_REFERRAL]phone:..|name:..|restaurant_id:..[/CREATE_REFERRAL]
              [CREATE_SQUAD]name:..|emoji:..[/CREATE_SQUAD]
              [ADD_FRIEND]method:phone|value:..[/ADD_FRIEND]
            """.formatted(req.context() != null ? req.context().toString() : "anonyme");

        List<AiDtos.ChatMessage> messages = new java.util.ArrayList<>();
        if (req.history() != null) messages.addAll(req.history());
        messages.add(new AiDtos.ChatMessage("user", req.message()));

        streamingClient.streamChat(emitter, systemPrompt, messages, 768);
        return emitter;
    }

    @PostMapping(value = "/assistant/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "Sprint C — Assistant Pocket streaming SSE (port EF oneclick-ai-assistant)",
        description = "Stream avec action blocks (RESERVATION/REFERRAL/SQUAD/ADD_FRIEND/CANCEL_RESERVATION)"
    )
    @PreAuthorize("hasAuthority('VIEW:LOYALTY')")
    public SseEmitter assistantStream(@Valid @RequestBody AiDtos.AssistantRequestDto req) {
        SseEmitter emitter = new SseEmitter(300_000L);

        String systemPrompt = """
            Tu es l'assistant personnel OneClick d'un utilisateur. Tu aides à :
            - Trouver un restaurant qui matche ses goûts
            - Planifier une réservation
            - Comprendre son programme de fidélité (points, paliers)
            - Suggérer des activités selon le contexte
            Ton style : amical, concis, marocain-friendly (références culturelles OK).
            Maximum 4 phrases. Contexte user : %s

            Tu peux émettre des blocs d'action si pertinent :
              [CREATE_RESERVATION]restaurant_id:..|date:..|heure:..|couverts:..[/CREATE_RESERVATION]
              [CREATE_REFERRAL]phone:..|name:..|restaurant_id:..[/CREATE_REFERRAL]
              [CREATE_SQUAD]name:..|emoji:..[/CREATE_SQUAD]
              [ADD_FRIEND]method:phone|value:..[/ADD_FRIEND]
              [CANCEL_RESERVATION]reservation_id:..|reason:..[/CANCEL_RESERVATION]
            """.formatted(req.context() != null ? req.context().toString() : "non fourni");

        streamingClient.streamChat(
            emitter,
            systemPrompt,
            List.of(new AiDtos.ChatMessage("user", req.prompt())),
            1024
        );
        return emitter;
    }
}
