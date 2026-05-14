package com.onesley.oneclick.modules.support.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.support.api.SupportDtos.TicketDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/** Ticket support client. */
@Entity
@Table(name = "support_tickets")
public class SupportTicket extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "opened_by", nullable = false, insertable = false, updatable = false) private UUID openedById;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "opened_by", nullable = false) private User openedBy;
    @NotBlank @Column(name = "category", nullable = false) private String category;
    @Pattern(regexp = "^(low|normal|high|urgent)$") @Column(name = "priority", nullable = false) private String priority = "normal";
    @Pattern(regexp = "^(open|in_progress|resolved|closed)$") @Column(name = "status", nullable = false) private String status = "open";
    @NotBlank @Column(name = "subject", nullable = false) private String subject;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "assigned_to", insertable = false, updatable = false) private UUID assignedToId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assigned_to") private User assignedTo;

    // ─── V19 — Sprint G.2.6 (enrich legacy parity) ─────────────────────────
    @Column(name = "restaurant_id") private UUID restaurantId;
    @Column(name = "photos", columnDefinition = "text[]") private String[] photos;
    @Column(name = "internal", nullable = false) private boolean internal = false;
    @Column(name = "escalated_to_admin", nullable = false) private boolean escalatedToAdmin = false;
    @Column(name = "last_reply") private String lastReply;
    @Column(name = "ai_handled", nullable = false) private boolean aiHandled = false;
    @Column(name = "ai_summary") private String aiSummary;

    // ─── V24 — Sprint K : corps initial du ticket (legacy parity) ────────────
    @Column(name = "message") private String message;

    protected SupportTicket() {}
    public SupportTicket(UUID id, User openedBy, String category, String subject) {
        this.id = id; this.openedBy = openedBy; this.category = category; this.subject = subject;
    }

    public UUID getId() { return id; }
    public UUID getOpenedById() { return openedById; }
    public User getOpenedBy() { return openedBy; }
    public String getCategory() { return category; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void markResolved() { this.resolvedAt = Instant.now(); this.status = "resolved"; }
    public Instant getClosedAt() { return closedAt; }
    public void markClosed() { this.closedAt = Instant.now(); this.status = "closed"; }
    public UUID getAssignedToId() { return assignedToId; }
    public User getAssignedTo() { return assignedTo; }
    public void setAssignedTo(User assignedTo) { this.assignedTo = assignedTo; }

    // V19 getters/setters
    public UUID getRestaurantId() { return restaurantId; }
    public void setRestaurantId(UUID restaurantId) { this.restaurantId = restaurantId; }
    public String[] getPhotos() { return photos; }
    public void setPhotos(String[] photos) { this.photos = photos; }
    public boolean isInternal() { return internal; }
    public void setInternal(boolean internal) { this.internal = internal; }
    public boolean isEscalatedToAdmin() { return escalatedToAdmin; }
    public void setEscalatedToAdmin(boolean escalatedToAdmin) { this.escalatedToAdmin = escalatedToAdmin; }
    public String getLastReply() { return lastReply; }
    public void setLastReply(String lastReply) { this.lastReply = lastReply; }
    public boolean isAiHandled() { return aiHandled; }
    public void setAiHandled(boolean aiHandled) { this.aiHandled = aiHandled; }
    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    /** Mapping vers le DTO public exposé hors du module. */
    public TicketDto toDto() {
        return new TicketDto(id, openedById, category, priority, status, subject, resolvedAt, closedAt,
            assignedToId,
            restaurantId,
            photos != null ? java.util.Arrays.asList(photos) : java.util.List.of(),
            internal, escalatedToAdmin, lastReply, aiHandled, aiSummary, message,
            getCreatedAt(), getUpdatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((SupportTicket) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
