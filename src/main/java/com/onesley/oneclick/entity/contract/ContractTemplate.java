package com.onesley.oneclick.entity.contract;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.tenant.Tenant;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.BatchSize;
import org.hibernate.proxy.HibernateProxy;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Entité {@code public.contract_templates} — templates de contrats partenaires
 * avec articles structurés (rendu PDF).
 *
 * <h3>Jointures JPA (passe 3) — aggregate root</h3>
 * <ul>
 *   <li>{@code tenant_id} → {@link Tenant} en {@code @ManyToOne(LAZY)}, nullable
 *       (templates globaux OneClick standard sans tenant_id).</li>
 *   <li>{@code @OneToMany articles} (ContractTemplateArticle) cascade ALL +
 *       orphanRemoval — un template est versionné en bloc avec ses articles.</li>
 * </ul>
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

    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @OneToMany(mappedBy = "template", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private Set<ContractTemplateArticle> articles = new HashSet<>();

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
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public Set<ContractTemplateArticle> getArticles() { return articles; }

    public void addArticle(ContractTemplateArticle a) {
        articles.add(a);
        a.setTemplate(this);
    }

    public void removeArticle(ContractTemplateArticle a) {
        articles.remove(a);
        a.setTemplate(null);
    }

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
        ContractTemplate that = (ContractTemplate) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
