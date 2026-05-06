package com.onesley.oneclick.entity.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.v_admin_audit_log} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : vue read-only @Immutable.
 */
@Entity
@Immutable
@Table(name = "v_admin_audit_log")
public class AdminAuditLogView {

    @Id
    @Column(name = "id", insertable = false, updatable = false)
    private UUID id;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "actor_id", insertable = false, updatable = false)
    private UUID actorId;

    @Column(name = "actor_email", insertable = false, updatable = false)
    private String actorEmail;

    @Column(name = "actor_name", insertable = false, updatable = false)
    private String actorName;

    @Column(name = "action", insertable = false, updatable = false)
    private String action;

    @Column(name = "entity_type", insertable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", insertable = false, updatable = false)
    private UUID entityId;

    @Column(name = "entity_label", insertable = false, updatable = false)
    private String entityLabel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diff", insertable = false, updatable = false)
    private Map<String, Object> diff = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", insertable = false, updatable = false)
    private Map<String, Object> metadata = new HashMap<>();

    protected AdminAuditLogView() {
        // JPA
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getActorId() { return actorId; }
    public String getActorEmail() { return actorEmail; }
    public String getActorName() { return actorName; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getEntityLabel() { return entityLabel; }
    public Map<String, Object> getDiff() { return diff; }
    public Map<String, Object> getMetadata() { return metadata; }
}
