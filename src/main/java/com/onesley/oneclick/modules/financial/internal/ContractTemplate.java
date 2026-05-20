package com.onesley.oneclick.modules.financial.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractTemplateDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractTemplate extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Tenant propriétaire — null pour les templates platform-wide. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    /** Code fonctionnel (ex: {@code "partner_contract"}, {@code "tos"}). */
    
    @Column(name = "code", nullable = false, length = 64)
     private String code;

    /** Libellé interne (admin-facing). */
    
    @Column(name = "name", nullable = false, length = 128)
    @Setter private String name;

    /** Numéro de version — incrément à chaque révision juridique. */
    
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /** Langue ISO 639-1 — {@code fr | en | ar} (CHECK V13). */
    
    @Column(name = "language", nullable = false, length = 64)
     private String language = "fr";

    /** Titre affiché dans le PDF. */
    
    @Column(name = "title", nullable = false, length = 128)
    @Setter private String title;

    /** Corps du contrat — markdown ou texte brut, rendu par le générateur PDF. */
    
    @Column(name = "body", nullable = false, columnDefinition = "text")
    @Setter private String body;

    @Column(name = "is_active", nullable = false)
    @Setter private boolean active = true;

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
