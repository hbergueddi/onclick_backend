package com.onesley.oneclick.entity.contract;

import com.onesley.oneclick.audit.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.contract_disabled_articles} — articles désactivés
 * spécifiquement pour un contrat (override des templates).
 *
 * <h3>Jointures JPA (passe 3) — entité d'association</h3>
 * <ul>
 *   <li>{@code contract_id NOT NULL} → {@link PartnerContract} en {@code @ManyToOne(LAZY)},
 *       optional=false. Côté inverse : {@link PartnerContract#getDisabledArticles()}
 *       cascade ALL + orphanRemoval.</li>
 *   <li>{@code article_id NOT NULL} → {@link ContractTemplateArticle} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code disabled_by} : audit field, reste UUID brut.</li>
 * </ul>
 */
@Entity
@Table(name = "contract_disabled_articles")
public class ContractDisabledArticle extends CreatedAtEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "contract_id", nullable = false, insertable = false, updatable = false)
    private UUID contractId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private PartnerContract contract;

    @Column(name = "article_id", nullable = false, insertable = false, updatable = false)
    private UUID articleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private ContractTemplateArticle article;

    /** Audit field : UUID brut. */
    @Column(name = "disabled_by")
    private UUID disabledBy;

    @Column(name = "reason")
    private String reason;

    protected ContractDisabledArticle() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public PartnerContract getContract() { return contract; }
    /** Package-private : appelé par les helpers du parent {@link PartnerContract#addDisabledArticle}/{@code removeDisabledArticle}. */
    void setContract(PartnerContract contract) { this.contract = contract; }
    public UUID getArticleId() { return articleId; }
    public ContractTemplateArticle getArticle() { return article; }
    public void setArticle(ContractTemplateArticle article) { this.article = article; }
    public UUID getDisabledBy() { return disabledBy; }
    public String getReason() { return reason; }

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
        ContractDisabledArticle that = (ContractDisabledArticle) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
