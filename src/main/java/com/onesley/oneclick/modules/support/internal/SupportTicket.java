package com.onesley.oneclick.modules.support.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.support.api.SupportDtos.TicketDto;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ticket support client. */
@Entity
@Table(name = "support_tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportTicket extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "opened_by", nullable = false, insertable = false, updatable = false) private UUID openedById;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "opened_by", nullable = false) private User openedBy;
      @Column(name = "category", nullable = false, length = 64) private String category;
      @Column(name = "priority", nullable = false, length = 64) @Setter private String priority = "normal";
      @Column(name = "status", nullable = false, length = 64) @Setter private String status = "open";
      @Column(name = "subject", nullable = false, length = 128) @Setter private String subject;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "assigned_to", insertable = false, updatable = false) private UUID assignedToId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assigned_to") @Setter private User assignedTo;

    // ─── V19 — Sprint G.2.6 (enrich legacy parity) ─────────────────────────
    @Column(name = "restaurant_id") @Setter private UUID restaurantId;
    @Column(name = "photos", columnDefinition = "text[]") @Setter private String[] photos;
    @Column(name = "internal", nullable = false) @Setter private boolean internal = false;
    @Column(name = "escalated_to_admin", nullable = false) @Setter private boolean escalatedToAdmin = false;
     @Column(name = "last_reply", length = 1024) @Setter private String lastReply;
    @Column(name = "ai_handled", nullable = false) @Setter private boolean aiHandled = false;
     @Column(name = "ai_summary", length = 1024) @Setter private String aiSummary;

    // ─── V24 — Sprint K : corps initial du ticket (legacy parity) ────────────
     @Column(name = "message", length = 1024) @Setter private String message;
    public SupportTicket(UUID id, User openedBy, String category, String subject) {
        this.id = id; this.openedBy = openedBy; this.category = category; this.subject = subject;
    }
    public void markResolved() { this.resolvedAt = Instant.now(); this.status = "resolved"; }
    public void markClosed() { this.closedAt = Instant.now(); this.status = "closed"; }

    // V19 getters/setters

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
