package com.onesley.oneclick.entity.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.contract_template_articles} — articles d'un template
 * (aggregate member de {@link ContractTemplate}).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code template_id NOT NULL} → {@link ContractTemplate} en {@code @ManyToOne(LAZY)}, optional=false.
 *       Côté inverse : {@link ContractTemplate#getArticles()} cascade ALL + orphanRemoval.</li>
 * </ul>
 */
@Entity
@Table(name = "contract_template_articles")
@EntityListeners(AuditingEntityListener.class)
public class ContractTemplateArticle {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "template_id", nullable = false, insertable = false, updatable = false)
    private UUID templateId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ContractTemplate template;

    @NotNull
    @Column(name = "article_number", nullable = false)
    private Integer articleNumber;

    @NotBlank
    @Column(name = "title", nullable = false)
    private String title;

    @NotBlank
    @Column(name = "content", nullable = false)
    private String content;

    @NotNull
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ContractTemplateArticle() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getTemplateId() { return templateId; }
    public ContractTemplate getTemplate() { return template; }
    /** Package-private : appelé par les helpers du parent {@link ContractTemplate#addArticle}/{@code removeArticle}. */
    void setTemplate(ContractTemplate template) { this.template = template; }
    public Integer getArticleNumber() { return articleNumber; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Integer getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }

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
        ContractTemplateArticle that = (ContractTemplateArticle) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
