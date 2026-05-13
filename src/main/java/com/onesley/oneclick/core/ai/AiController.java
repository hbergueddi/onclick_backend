package com.onesley.oneclick.core.ai;

import com.onesley.oneclick.core.ai.api.AiDtos;
import com.onesley.oneclick.core.ai.api.AiDtos.AiResponseDto;
import com.onesley.oneclick.core.ai.internal.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    public AiController(AiService service) {
        this.service = service;
    }

    @PostMapping("/care-chat")
    @Operation(summary = "Support chat — répond aux questions client avec contexte tickets")
    @PreAuthorize("isAuthenticated()")
    public AiResponseDto careChat(@Valid @RequestBody AiDtos.CareChatRequestDto req) {
        return service.careChat(req);
    }

    @PostMapping("/assistant")
    @Operation(summary = "Assistant Pocket — répond aux prompts user (rate-limit 5/jour côté frontend)")
    @PreAuthorize("isAuthenticated()")
    public AiResponseDto assistant(@Valid @RequestBody AiDtos.AssistantRequestDto req) {
        return service.assistant(req);
    }

    @PostMapping("/elite-review")
    @Operation(summary = "Résumé reviews — analyse N avis client d'un restaurant")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public AiResponseDto eliteReview(@Valid @RequestBody AiDtos.EliteReviewRequestDto req) {
        return service.eliteReview(req);
    }

    @PostMapping("/plan")
    @Operation(summary = "Génération plan hebdomadaire — planning service auto")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public AiResponseDto plan(@Valid @RequestBody AiDtos.PlanRequestDto req) {
        return service.plan(req);
    }
}
