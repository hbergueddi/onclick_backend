package com.onesley.oneclick.modules.financial.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractTemplateDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

/**
 * Template contractuel versionné & multilangue — utilisé par ContractDownload
 * pour générer les PDFs de contrat partenaire (cf. V13 §4).
 *
 * <p>Clé fonctionnelle unique : {@code (tenant_id, code, version, language)}.
 * {@code tenant_id} peut être {@code null} = template "platform-wide"
 * (fallback pour les tenants qui n'ont pas leur propre version).
 */
@Entity
@Table(name = "contract_templates")
public class ContractTemplate extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Tenant propriétaire — null pour les templates platform-wide. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    /** Code fonctionnel (ex: {@code "partner_contract"}, {@code "tos"}). */
    @NotBlank
    @Column(name = "code", nullable = false)
    private String code;

    /** Libellé interne (admin-facing). */
    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    /** Numéro de version — incrément à chaque révision juridique. */
    @NotNull
    @Positive
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /** Langue ISO 639-1 — {@code fr | en | ar} (CHECK V13). */
    @NotBlank
    @Pattern(regexp = "^(fr|en|ar)$")
    @Column(name = "language", nullable = false)
    private String language = "fr";

    /** Titre affiché dans le PDF. */
    @NotBlank
    @Column(name = "title", nullable = false)
    private String title;

    /** Corps du contrat — markdown ou texte brut, rendu par le générateur PDF. */
    @NotBlank
    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected ContractTemplate() {}

    public ContractTemplate(UUID id, UUID tenantId, String code, String name,
                            Integer version, String language, String title, String body) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        if (version != null) this.version = version;
        if (language != null) this.language = language;
        this.title = title;
        this.body = body;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getVersion() { return version; }
    public String getLanguage() { return language; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    /** Mapping vers le DTO public exposé hors du module. */
    public ContractTemplateDto toDto() {
        return new ContractTemplateDto(
            id, tenantId, code, name, version, language,
            title, body, active, getCreatedAt(), getUpdatedAt()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((ContractTemplate) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
