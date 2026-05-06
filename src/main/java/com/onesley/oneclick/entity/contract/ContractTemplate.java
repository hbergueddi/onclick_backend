package com.onesley.oneclick.entity.contract;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Entité {@code public.contract_templates} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "contract_templates")
public class ContractTemplate extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Column(name = "version", nullable = false)
    private String version;

    @NotBlank
    @Column(name = "preamble", nullable = false)
    private String preamble;

    @NotBlank
    @Column(name = "footer", nullable = false)
    private String footer;

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "tenant_id")
    private UUID tenantId;

    protected ContractTemplate() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getPreamble() { return preamble; }
    public String getFooter() { return footer; }
    public Boolean getIsActive() { return isActive; }
    public UUID getTenantId() { return tenantId; }
}
