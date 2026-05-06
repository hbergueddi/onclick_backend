package com.onesley.oneclick.entity.auth;

import com.onesley.oneclick.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.custom_roles} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : audit niveau 1 (4 colonnes).
 */
@Entity
@Table(name = "custom_roles")
public class CustomRole extends AuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> permissions = new HashMap<>();

    protected CustomRole() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Map<String, Object> getPermissions() { return permissions; }
}
