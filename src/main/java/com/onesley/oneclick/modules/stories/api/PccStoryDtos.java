package com.onesley.oneclick.modules.stories.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs publics « Stories » (PCC) exposés par {@code PccStoryController}.
 *
 * <p>Records immutables + Bean Validation sur les payloads d'entrée (mediaUrl non vide ≤ 2048 ;
 * caption ≤ 280 ; mediaType ∈ {@code image|video} ; durationS 1-120). Pas de logique de mapping
 * ici : la conversion Entity → {@link StoryDto} se fait dans {@code PccStoryService}.</p>
 */
public final class PccStoryDtos {

    private PccStoryDtos() {}

    /**
     * Payload de création d'une story (staff de gestion). {@code mediaType} optionnel (défaut
     * {@code image} côté service) ; {@code durationS}/{@code sortOrder} optionnels (défauts 15 / 0) ;
     * {@code publishAt} optionnel (défaut now ; futur = programmée) ; {@code caption}/{@code expiresAt}
     * optionnels.
     *
     * <p>{@code durationS}/{@code sortOrder}/{@code publishAt} sont des objets (Integer/Instant) pour
     * distinguer « non fourni » (→ défaut) de la valeur — un record ne supporte pas les valeurs par
     * défaut, le service applique les défauts sur null.</p>
     */
    public record CreateStoryDto(
        @NotBlank @Size(min = 1, max = 2048) String mediaUrl,
        @Pattern(regexp = "^(image|video)$",
            message = "mediaType doit être 'image' ou 'video'") String mediaType,
        @Size(max = 280) String caption,
        @Min(1) @Max(120) Integer durationS,
        Integer sortOrder,
        Instant publishAt,
        Instant expiresAt
    ) {}

    /**
     * Payload d'édition d'une story (staff de gestion). Mêmes champs que la création (remplacement
     * complet — sémantique PATCH simplifiée : le front renvoie l'état souhaité).
     */
    public record UpdateStoryDto(
        @NotBlank @Size(min = 1, max = 2048) String mediaUrl,
        @Pattern(regexp = "^(image|video)$",
            message = "mediaType doit être 'image' ou 'video'") String mediaType,
        @Size(max = 280) String caption,
        @Min(1) @Max(120) Integer durationS,
        Integer sortOrder,
        Instant publishAt,
        Instant expiresAt
    ) {}

    /**
     * Une story (payload REST).
     *
     * <p>Enrichie du nom de l'auteur (read via {@code UserDirectoryApi}) et de l'état de visibilité
     * ({@code visible} = vivante + publiée + non expirée à l'instant courant — utile au pilotage
     * staff). {@code expiresAt}/{@code deletedAt} exposés pour la gestion.</p>
     */
    public record StoryDto(
        UUID id,
        UUID tenantId,
        UUID authorId,
        String authorFirstName,
        String authorLastName,
        String mediaUrl,
        String mediaType,
        String caption,
        int durationS,
        int sortOrder,
        Instant publishAt,
        Instant expiresAt,
        boolean visible,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt,
        /** Gap #7 — la story a-t-elle été vue par le caller (ring unread/read côté membre). */
        boolean viewed
    ) {}

    /** Gap #7 — nombre de vues d'une story (stats staff/admin « vue par X membres »). */
    public record StoryViewCountDto(UUID storyId, long viewCount) {}
}
