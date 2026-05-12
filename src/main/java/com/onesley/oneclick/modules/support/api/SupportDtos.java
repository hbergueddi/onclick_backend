package com.onesley.oneclick.modules.support.api;

import jakarta.validation.constraints.NotBlank;
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
                            Instant createdAt, Instant updatedAt) {}

    public record TicketCreateDto(
        @NotNull UUID openedById,
        @NotBlank String category,
        @NotBlank String subject,
        @Pattern(regexp = "^(low|normal|high|urgent)$") String priority
    ) {}

    public record TicketUpdateDto(
        @Pattern(regexp = "^(open|in_progress|resolved|closed)$") String status,
        @Pattern(regexp = "^(low|normal|high|urgent)$") String priority,
        UUID assignedToId
    ) {}

    // ─── Message ─────────────────────────────────────────────────────────────

    public record MessageDto(UUID id, UUID ticketId, UUID authorId, String message, Instant createdAt) {}

    public record MessageCreateDto(
        @NotNull UUID ticketId,
        @NotNull UUID authorId,
        @NotBlank String message
    ) {}

    // ─── Attachment ──────────────────────────────────────────────────────────

    public record AttachmentDto(UUID id, UUID ticketId, String url, String fileName, String mimeType, Instant createdAt) {}

    public record AttachmentCreateDto(
        @NotNull UUID ticketId,
        @NotBlank String url,
        String fileName,
        String mimeType
    ) {}
}
