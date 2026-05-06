package com.onesley.oneclick.entity.support;

import com.onesley.oneclick.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.support_tickets} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : audit niveau 1 (4 colonnes).
 */
@Entity
@Table(name = "support_tickets")
public class SupportTicket extends AuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "message", nullable = false)
    private String message;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "photos", columnDefinition = "text[]")
    private List<String> photos = new ArrayList<>();

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "last_reply")
    private String lastReply;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(name = "ticket_type", nullable = false)
    private String ticketType;

    @Column(name = "escalated_to_admin", nullable = false)
    private Boolean escalatedToAdmin;

    @Column(name = "priority", nullable = false)
    private String priority;

    @Column(name = "resolution_level", nullable = false)
    private String resolutionLevel;

    @Column(name = "ai_handled", nullable = false)
    private Boolean aiHandled;

    @Column(name = "ai_summary")
    private String aiSummary;

    protected SupportTicket() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public String getCategory() { return category; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public List<String> getPhotos() { return photos; }
    public String getStatus() { return status; }
    public String getLastReply() { return lastReply; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getTicketType() { return ticketType; }
    public Boolean getEscalatedToAdmin() { return escalatedToAdmin; }
    public String getPriority() { return priority; }
    public String getResolutionLevel() { return resolutionLevel; }
    public Boolean getAiHandled() { return aiHandled; }
    public String getAiSummary() { return aiSummary; }
}
