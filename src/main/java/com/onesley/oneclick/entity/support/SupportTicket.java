package com.onesley.oneclick.entity.support;

import com.onesley.oneclick.audit.AuditedEntity;
import com.onesley.oneclick.entity.status.EntityStatus;
import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.restaurant.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.support_tickets} — tickets support (chat IA + escalation
 * humaine) avec photos, priority, AI handling.
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code client_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code restaurant_id} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, nullable
 *       (ticket peut être global, sans resto précis).</li>
 *   <li>Pas d'inverse {@code @OneToMany messages} : volume potentiellement élevé,
 *       repository paginé à la place.</li>
 * </ul>
 */
@Entity
@Table(name = "support_tickets")
public class SupportTicket extends AuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false, insertable = false, updatable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Profile client;

    @NotBlank
    @Column(name = "category", nullable = false)
    private String category;

    @NotBlank
    @Column(name = "subject", nullable = false)
    private String subject;

    @NotBlank
    @Column(name = "message", nullable = false)
    private String message;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "photos", columnDefinition = "text[]")
    private List<String> photos = new ArrayList<>();

    @Column(name = "status_id", nullable = false, insertable = false, updatable = false)
    private UUID statusId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private EntityStatus status;

    @Column(name = "last_reply")
    private String lastReply;

    @Column(name = "restaurant_id", insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @NotBlank
    @Column(name = "ticket_type", nullable = false)
    private String ticketType;

    @NotNull
    @Column(name = "escalated_to_admin", nullable = false)
    private Boolean escalatedToAdmin;

    @NotBlank
    @Column(name = "priority", nullable = false)
    private String priority;

    @NotBlank
    @Column(name = "resolution_level", nullable = false)
    private String resolutionLevel;

    @NotNull
    @Column(name = "ai_handled", nullable = false)
    private Boolean aiHandled;

    @Column(name = "ai_summary")
    private String aiSummary;

    protected SupportTicket() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public Profile getClient() { return client; }
    public void setClient(Profile client) { this.client = client; }
    public String getCategory() { return category; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public List<String> getPhotos() { return photos; }
    public UUID getStatusId() { return statusId; }
    public EntityStatus getStatus() { return status; }
    public void setStatus(EntityStatus status) { this.status = status; }
    public String getLastReply() { return lastReply; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
    public String getTicketType() { return ticketType; }
    public Boolean getEscalatedToAdmin() { return escalatedToAdmin; }
    public String getPriority() { return priority; }
    public String getResolutionLevel() { return resolutionLevel; }
    public Boolean getAiHandled() { return aiHandled; }
    public String getAiSummary() { return aiSummary; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        SupportTicket that = (SupportTicket) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
