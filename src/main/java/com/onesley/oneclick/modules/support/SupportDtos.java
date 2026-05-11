package com.onesley.oneclick.modules.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

public final class SupportDtos {

    private SupportDtos() {}

    // ─── Ticket ──────────────────────────────────────────────────────────────

    public record TicketDto(UUID id, UUID openedById, String category, String priority, String status,
                            String subject, Instant resolvedAt, Instant closedAt, UUID assignedToId,
                            Instant createdAt, Instant updatedAt) {
        public static TicketDto from(SupportTicket t) {
            return new TicketDto(t.getId(), t.getOpenedById(), t.getCategory(), t.getPriority(),
                t.getStatus(), t.getSubject(), t.getResolvedAt(), t.getClosedAt(), t.getAssignedToId(),
                t.getCreatedAt(), t.getUpdatedAt());
        }
    }

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

    public record MessageDto(UUID id, UUID ticketId, UUID authorId, String message, Instant createdAt) {
        public static MessageDto from(TicketMessage m) {
            return new MessageDto(m.getId(), m.getTicketId(), m.getAuthorId(), m.getMessage(), m.getCreatedAt());
        }
    }

    public record MessageCreateDto(
        @NotNull UUID ticketId,
        @NotNull UUID authorId,
        @NotBlank String message
    ) {}

    // ─── Attachment ──────────────────────────────────────────────────────────

    public record AttachmentDto(UUID id, UUID ticketId, String url, String fileName, String mimeType, Instant createdAt) {
        public static AttachmentDto from(TicketAttachment a) {
            return new AttachmentDto(a.getId(), a.getTicketId(), a.getUrl(), a.getFileName(), a.getMimeType(), a.getCreatedAt());
        }
    }

    public record AttachmentCreateDto(
        @NotNull UUID ticketId,
        @NotBlank String url,
        String fileName,
        String mimeType
    ) {}
}
