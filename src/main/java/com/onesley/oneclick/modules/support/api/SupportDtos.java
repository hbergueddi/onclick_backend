package com.onesley.oneclick.modules.support.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs publics du module support.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Entity.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 */
public final class SupportDtos {

    private SupportDtos() {}

    // ─── Ticket ──────────────────────────────────────────────────────────────

    public record TicketDto(UUID id, UUID openedById, String category, String priority, String status,
                            String subject, Instant resolvedAt, Instant closedAt, UUID assignedToId,
                            // V19 — Sprint G.2.6 (enrich legacy parity)
                            UUID restaurantId,
                            java.util.List<String> photos,
                            boolean internal,
                            boolean escalatedToAdmin,
                            String lastReply,
                            boolean aiHandled,
                            String aiSummary,
                            // V24 — Sprint K
                            String message,
                            Instant createdAt, Instant updatedAt,
                            // Enrichissement serveur-side du profil de l'auteur (openedBy) — peuplé par
                            // SupportService.findAll via le domaine identity. Évite /api/users/by-ids
                            // (VIEW:USERS). null sur findById/create/update (mapping non enrichi).
                            String openedByFirstName, String openedByLastName, String openedByPhone) {}

    public record TicketCreateDto(
        @NotNull UUID openedById,
        @NotBlank @Size(min = 1, max = 64) String category,
        @NotBlank @Size(min = 1, max = 128) String subject,
        @Pattern(regexp = "^(low|normal|high|urgent)$") @Size(min = 1, max = 64) String priority,
        // V24 — Sprint K : corps + rattachement resto + statut initial optionnels
        @Size(min = 1, max = 1024) String message,
        UUID restaurantId,
        @Pattern(regexp = "^(open|in_progress|resolved|closed)$") @Size(min = 1, max = 64) String status
    ) {}

    public record TicketUpdateDto(
        @Pattern(regexp = "^(open|in_progress|resolved|closed)$") @Size(min = 1, max = 64) String status,
        @Pattern(regexp = "^(low|normal|high|urgent)$") @Size(min = 1, max = 64) String priority,
        UUID assignedToId,
        // V24 — Sprint K : dernière réponse staff
        @Size(min = 1, max = 1024) String lastReply,
        // Escalade vers l'admin (lecture exposée sur TicketDto ; écrit par le bouton "Escalader" ProDesk)
        Boolean escalatedToAdmin
    ) {}

    // ─── Message ─────────────────────────────────────────────────────────────

    public record MessageDto(UUID id, UUID ticketId, UUID authorId, String message, Instant createdAt) {}

    public record MessageCreateDto(
        @NotNull UUID ticketId,
        @NotNull UUID authorId,
        @NotBlank @Size(min = 1, max = 1024) String message
    ) {}

    // ─── Attachment ──────────────────────────────────────────────────────────

    public record AttachmentDto(UUID id, UUID ticketId, String url, String fileName, String mimeType, Instant createdAt) {}

    public record AttachmentCreateDto(
        @NotNull UUID ticketId,
        @NotBlank @Size(min = 1, max = 512) String url,
        @Size(min = 1, max = 128) String fileName,
        @Size(min = 1, max = 64) String mimeType
    ) {}
}
