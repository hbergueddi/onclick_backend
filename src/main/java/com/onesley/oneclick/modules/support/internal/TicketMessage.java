package com.onesley.oneclick.modules.support.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.support.api.SupportDtos.MessageDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/** Message dans un thread support. */
@Entity
@Table(name = "ticket_messages")
@EntityListeners(AuditingEntityListener.class)
public class TicketMessage {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "ticket_id", nullable = false, insertable = false, updatable = false) private UUID ticketId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ticket_id", nullable = false) private SupportTicket ticket;
    @Column(name = "author_id", nullable = false, insertable = false, updatable = false) private UUID authorId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id", nullable = false) private User author;
    @NotBlank @Column(name = "message", nullable = false) private String message;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;

    protected TicketMessage() {}
    public TicketMessage(UUID id, SupportTicket ticket, User author, String message) {
        this.id = id; this.ticket = ticket; this.author = author; this.message = message;
    }

    public UUID getId() { return id; }
    public UUID getTicketId() { return ticketId; }
    public SupportTicket getTicket() { return ticket; }
    public UUID getAuthorId() { return authorId; }
    public User getAuthor() { return author; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }

    /** Mapping vers le DTO public exposé hors du module. */
    public MessageDto toDto() {
        return new MessageDto(id, ticketId, authorId, message, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((TicketMessage) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
