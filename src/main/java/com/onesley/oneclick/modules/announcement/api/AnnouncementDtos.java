package com.onesley.oneclick.modules.announcement.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs publics « Annonces tenant » (Lot 8) exposés par {@code AnnouncementController}.
 *
 * <p>Records immutables + Bean Validation sur les payloads d'entrée (title 1-100, body 1-1000,
 * priority ∈ {@code urgent|permanent}). Pas de logique de mapping ici : la conversion
 * Entity → {@link AnnouncementDto} se fait dans {@code AnnouncementService}.</p>
 */
public final class AnnouncementDtos {

    private AnnouncementDtos() {}

    /**
     * Payload de création d'une annonce (tenant-admin). {@code priority} optionnel (défaut
     * {@code permanent} côté service) ; {@code pinned} optionnel (défaut true) ; {@code publishAt}
     * optionnel (défaut now ; futur = programmée) ; {@code imageUrl} optionnel.
     *
     * <p>{@code priority}/{@code pinned}/{@code publishAt} sont des objets (Boolean/Instant) pour
     * distinguer « non fourni » (→ défaut) de la valeur — un record ne supporte pas les valeurs par
     * défaut, le service applique les défauts sur null.</p>
     */
    public record CreateAnnouncementDto(
        @NotBlank @Size(min = 1, max = 100) String title,
        @NotBlank @Size(min = 1, max = 1000) String body,
        String imageUrl,
        @Pattern(regexp = "^(urgent|permanent)$",
            message = "priority doit être 'urgent' ou 'permanent'") String priority,
        Boolean pinned,
        Instant publishAt
    ) {}

    /**
     * Payload d'édition d'une annonce (tenant-admin). Mêmes champs que la création (remplacement
     * complet — sémantique PATCH simplifiée : le front renvoie l'état souhaité). Éditer le
     * {@code body} déclenche le bump body_version + le reset des acquittements côté service.
     */
    public record UpdateAnnouncementDto(
        @NotBlank @Size(min = 1, max = 100) String title,
        @NotBlank @Size(min = 1, max = 1000) String body,
        String imageUrl,
        @Pattern(regexp = "^(urgent|permanent)$",
            message = "priority doit être 'urgent' ou 'permanent'") String priority,
        Boolean pinned,
        Instant publishAt
    ) {}

    /**
     * Payload de marquage « lu » par un membre du staff. {@code bodyVersion} = la version que le
     * client a effectivement affichée (acquittée). Optionnel : si null, le service acquitte la
     * version courante de l'annonce (défense en profondeur).
     */
    public record MarkReadDto(
        Integer bodyVersion
    ) {}

    /**
     * Une annonce (payload REST + WebSocket STOMP).
     *
     * <p>Enrichie du nom de l'auteur (read via {@code UserDirectoryApi}) et de l'état de lecture du
     * caller ({@code readByMe} = a acquitté la {@code bodyVersion} courante ; {@code published} =
     * {@code publishAt <= now}). {@code archivedAt}/{@code deletedAt} exposés pour la gestion admin.</p>
     */
    public record AnnouncementDto(
        UUID id,
        UUID tenantId,
        UUID authorId,
        String authorFirstName,
        String authorLastName,
        String title,
        String body,
        String imageUrl,
        String priority,
        boolean pinned,
        Instant publishAt,
        boolean published,
        Instant archivedAt,
        Instant deletedAt,
        int bodyVersion,
        boolean readByMe,
        Instant createdAt,
        Instant updatedAt
    ) {}
}
