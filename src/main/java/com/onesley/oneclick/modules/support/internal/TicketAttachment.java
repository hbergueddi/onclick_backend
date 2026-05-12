package com.onesley.oneclick.modules.support.internal;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/** Pièce jointe d'un ticket support. */
@Entity
@Table(name = "ticket_attachments")
@EntityListeners(AuditingEntityListener.class)
public class TicketAttachment {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "ticket_id", nullable = false, insertable = false, updatable = false) private UUID ticketId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ticket_id", nullable = false) private SupportTicket ticket;
    @NotBlank @Column(name = "url", nullable = false) private String url;
    @Column(name = "file_name") private String fileName;
    @Column(name = "mime_type") private String mimeType;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;

    protected TicketAttachment() {}
    public TicketAttachment(UUID id, SupportTicket ticket, String url) {
        this.id = id; this.ticket = ticket; this.url = url;
    }

    public UUID getId() { return id; }
    public UUID getTicketId() { return ticketId; }
    public SupportTicket getTicket() { return ticket; }
    public String getUrl() { return url; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((TicketAttachment) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
