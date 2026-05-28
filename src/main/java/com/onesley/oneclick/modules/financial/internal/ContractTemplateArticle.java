package com.onesley.oneclick.modules.financial.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractTemplateArticleDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Article (clause) d'un template contractuel — table {@code contract_template_articles} (V55). */
@Entity
@Table(name = "contract_template_articles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractTemplateArticle extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "article_number", nullable = false)
    @Setter private Integer articleNumber;

    @Column(name = "title", nullable = false, length = 256)
    @Setter private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    @Setter private String content;

    @Column(name = "sort_order", nullable = false)
    @Setter private Integer sortOrder = 0;

    public ContractTemplateArticle(UUID id, UUID templateId, Integer articleNumber, String title, String content, Integer sortOrder) {
        this.id = id;
        this.templateId = templateId;
        this.articleNumber = articleNumber;
        this.title = title;
        this.content = content;
        if (sortOrder != null) this.sortOrder = sortOrder;
    }

    public ContractTemplateArticleDto toDto() {
        return new ContractTemplateArticleDto(id, templateId, articleNumber, title, content, sortOrder, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((ContractTemplateArticle) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
