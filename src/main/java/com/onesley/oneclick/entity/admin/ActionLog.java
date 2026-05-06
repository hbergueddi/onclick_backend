package com.onesley.oneclick.entity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.action_logs} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "action_logs")
@EntityListeners(AuditingEntityListener.class)
public class ActionLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotBlank
    @Column(name = "member_name", nullable = false)
    private String memberName;

    @NotBlank
    @Column(name = "action", nullable = false)
    private String action;

    @NotBlank
    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "details")
    private String details;

    @Column(name = "ip")
    private String ip;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActionLog() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public UUID getUserId() { return userId; }
    public String getMemberName() { return memberName; }
    public String getAction() { return action; }
    public String getType() { return type; }
    public String getDetails() { return details; }
    public String getIp() { return ip; }
    public Instant getCreatedAt() { return createdAt; }
}
