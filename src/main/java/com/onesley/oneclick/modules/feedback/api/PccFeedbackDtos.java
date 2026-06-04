package com.onesley.oneclick.modules.feedback.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs publics « Avis » (PCC Lot 7) exposés par {@code PccFeedbackController}.
 *
 * <p>Records immutables + Bean Validation sur les payloads d'entrée. Pas de logique de mapping
 * ici : la conversion Entity → {@link FeedbackDto} se fait dans {@code PccFeedbackService}.</p>
 */
public final class PccFeedbackDtos {

    private PccFeedbackDtos() {}

    /**
     * Payload d'envoi d'un avis (membre). {@code sentiment} ∈ {@code happy|unhappy} ;
     * {@code category} libre mais bornée ; {@code comment} optionnel (≤ 2000) ;
     * {@code targetRestaurantId} optionnel (NULL = avis général tenant-wide).
     */
    public record CreateFeedbackDto(
        @NotBlank @Pattern(regexp = "^(happy|unhappy)$",
            message = "sentiment doit être 'happy' ou 'unhappy'") String sentiment,
        @NotBlank @Size(min = 2, max = 128) String category,
        @Size(max = 2000) String comment,
        UUID targetRestaurantId
    ) {}

    /**
     * Payload de réponse d'un owner/admin à un avis. {@code replyText} borné (≤ 2000),
     * non vide (min 2 — cohérent avec le legacy {@code send-pcc-feedback-reply}).
     */
    public record ReplyFeedbackDto(
        @NotBlank @Size(min = 2, max = 2000) String replyText
    ) {}

    /**
     * Un avis du thread (payload REST + WebSocket STOMP).
     *
     * <p>Enrichi du nom/contact du membre (auteur) et du nom du resto ciblé (read-view) — utiles
     * à l'affichage de l'inbox owner. {@code replyText}/{@code replyBy}/{@code replyAt} renseignés
     * dès la réponse ; {@code replyReadByMember} suit la lecture côté membre.</p>
     */
    public record FeedbackDto(
        UUID id,
        UUID memberId,
        String memberFirstName,
        String memberLastName,
        String memberEmail,
        UUID tenantId,
        String sentiment,
        String category,
        String comment,
        UUID targetRestaurantId,
        String targetRestaurantName,
        String replyText,
        UUID replyBy,
        Instant replyAt,
        boolean replyReadByMember,
        Instant createdAt
    ) {}
}
