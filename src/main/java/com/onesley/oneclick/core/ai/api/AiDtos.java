package com.onesley.oneclick.core.ai.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * DTOs publics du module {@code core/ai} — Sprint B.1 G.4-bis.
 *
 * <p>4 endpoints (1 par EF Supabase portée) :
 * <ol>
 *   <li>{@link CareChatRequestDto} → {@link AiResponseDto} (support chat)</li>
 *   <li>{@link AssistantRequestDto} → {@link AiResponseDto} (Pocket AI 5/jour)</li>
 *   <li>{@link EliteReviewRequestDto} → {@link AiResponseDto} (review summarizer)</li>
 *   <li>{@link PlanRequestDto} → {@link AiResponseDto} (génération plan)</li>
 * </ol>
 *
 * <p>Tous utilisent le même format de réponse {@link AiResponseDto} pour la
 * cohérence : message texte + tokens consumés (pour analytics/cost tracking).
 */
public final class AiDtos {

    private AiDtos() {}

    // ─── Care chat (support) ─────────────────────────────────────────────

    public record CareChatRequestDto(
        @NotBlank @Size(max = 4000) String message,
        // Contexte client minimal (prénom + nb tickets ouverts) — pas de PII
        Map<String, Object> context,
        // Historique conversation (last N messages)
        List<ChatMessage> history
    ) {}

    // ─── AI Assistant (Pocket — 5/jour) ──────────────────────────────────

    public record AssistantRequestDto(
        @NotBlank @Size(max = 2000) String prompt,
        // Contexte personnalisation (préférences, tier, last reservations)
        Map<String, Object> context
    ) {}

    // ─── Elite review summarizer ─────────────────────────────────────────

    public record EliteReviewRequestDto(
        @NotBlank String restaurantId,
        // Liste de reviews à résumer (text + rating)
        @Size(min = 1, max = 50) List<ReviewInput> reviews
    ) {
        public record ReviewInput(String text, int rating) {}
    }

    // ─── Generate plan (hebdomadaire) ────────────────────────────────────

    public record PlanRequestDto(
        @NotBlank String restaurantId,
        // Format : "YYYY-MM-DD" début de semaine
        @NotBlank String weekStart,
        // Préférences planning : avec ou sans dimanche, brunch, etc.
        Map<String, Object> preferences
    ) {}

    // ─── Réponse commune ─────────────────────────────────────────────────

    /**
     * Réponse standardisée des 4 endpoints AI.
     *
     * @param message            Texte généré par le LLM (ou stub message si pas de key)
     * @param model              Modèle utilisé (ex: {@code llama-3.3-70b-versatile})
     * @param promptTokens       Tokens du prompt (pour analytics/cost)
     * @param completionTokens   Tokens générés
     * @param stubMode           {@code true} si la key Groq absente → réponse factice
     */
    public record AiResponseDto(
        String message,
        String model,
        int promptTokens,
        int completionTokens,
        boolean stubMode
    ) {
        public static AiResponseDto stub(String hint) {
            return new AiResponseDto(
                "[AI not configured — set GROQ_API_KEY in env to enable]. Stub : " + hint,
                "stub",
                0, 0,
                true
            );
        }
    }

    // ─── Helper records ──────────────────────────────────────────────────

    public record ChatMessage(
        @NotBlank String role,    // "user" | "assistant" | "system"
        @NotBlank String content
    ) {}
}
