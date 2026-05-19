package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.AiDtos;
import com.onesley.oneclick.core.ai.api.AiDtos.AiResponseDto;
import org.springframework.stereotype.Service;

import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Service AI — orchestration des 4 usages (care chat, assistant, review, plan).
 *
 * <p>Pattern senior : pas de logique business dans GroqClient (qui est juste HTTP).
 * Cette classe construit le {@code system prompt} adapté à chaque usage + appelle
 * {@link GroqClient} avec les paramètres adéquats.
 */
@Service
@RequiredArgsConstructor
public class AiService {

    private final GroqClient groq;

    /**
     * Chat support client — répond à des questions du support avec contexte
     * du compte client (prénom + nb tickets ouverts récents).
     */
    public AiResponseDto careChat(AiDtos.CareChatRequestDto req) {
        String systemPrompt = """
            Tu es un assistant support OneClick — plateforme de fidélisation restaurants au Maroc.
            Réponds en français, ton chaleureux mais concis. Maximum 3 phrases courtes.
            Si tu ne sais pas, dis-le honnêtement et propose de contacter l'équipe humaine.
            Contexte client : %s
            """.formatted(req.context() != null ? req.context().toString() : "anonyme");

        List<AiDtos.ChatMessage> messages = new java.util.ArrayList<>();
        if (req.history() != null) messages.addAll(req.history());
        messages.add(new AiDtos.ChatMessage("user", req.message()));

        return groq.chat(systemPrompt, messages, 512, "care chat: " + req.message().substring(0, Math.min(50, req.message().length())));
    }

    /**
     * AI Assistant Pocket — répond à des prompts user avec personnalisation.
     * Rate-limit 5/jour appliqué côté frontend (table ai_usage legacy).
     */
    public AiResponseDto assistant(AiDtos.AssistantRequestDto req) {
        String systemPrompt = """
            Tu es l'assistant personnel OneClick d'un utilisateur. Tu aides à :
            - Trouver un restaurant qui matche ses goûts
            - Planifier une réservation
            - Comprendre son programme de fidélité (points, paliers)
            - Suggérer des activités selon le contexte
            Ton style : amical, concis, marocain-friendly (références culturelles OK).
            Maximum 4 phrases. Contexte user : %s
            """.formatted(req.context() != null ? req.context().toString() : "non fourni");

        return groq.chat(
            systemPrompt,
            List.of(new AiDtos.ChatMessage("user", req.prompt())),
            768,
            "assistant: " + req.prompt().substring(0, Math.min(50, req.prompt().length()))
        );
    }

    /**
     * Elite review summarizer — résume N reviews d'un restaurant.
     */
    public AiResponseDto eliteReview(AiDtos.EliteReviewRequestDto req) {
        StringBuilder reviewsText = new StringBuilder();
        for (var r : req.reviews()) {
            reviewsText.append(String.format("[%d/5] %s%n", r.rating(), r.text()));
        }

        String systemPrompt = """
            Tu es un analyste de reviews restaurants. Résume les points clés
            de ces avis client en 5 lignes maximum : forces / faiblesses /
            recommandations pour le restaurateur. Ton factuel, pas marketing.
            """;

        return groq.chat(
            systemPrompt,
            List.of(new AiDtos.ChatMessage("user", reviewsText.toString())),
            512,
            "elite review for " + req.restaurantId()
        );
    }

    /**
     * Generate plan hebdomadaire — génère un planning service pour un restaurant.
     */
    public AiResponseDto plan(AiDtos.PlanRequestDto req) {
        String systemPrompt = """
            Tu génères un plan hebdomadaire pour un restaurant marocain.
            Format : lignes "Lun 11h-15h | brunch | 60 couverts max" pour les 7 jours.
            Prends en compte les préférences fournies. Maximum 7 lignes.
            """;

        String userPrompt = String.format(
            "Restaurant %s, semaine du %s. Préférences : %s",
            req.restaurantId(),
            req.weekStart(),
            req.preferences() != null ? req.preferences().toString() : "standard"
        );

        return groq.chat(
            systemPrompt,
            List.of(new AiDtos.ChatMessage("user", userPrompt)),
            512,
            "plan for " + req.restaurantId() + " week " + req.weekStart()
        );
    }
}
