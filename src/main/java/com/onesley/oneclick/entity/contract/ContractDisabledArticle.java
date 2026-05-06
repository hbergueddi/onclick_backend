package com.onesley.oneclick.entity.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.contract_disabled_articles} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "contract_disabled_articles")
@EntityListeners(AuditingEntityListener.class)
public class ContractDisabledArticle {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @NotNull
    @Column(name = "article_id", nullable = false)
    private UUID articleId;

    @Column(name = "disabled_by")
    private UUID disabledBy;

    @Column(name = "reason")
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ContractDisabledArticle() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public UUID getArticleId() { return articleId; }
    public UUID getDisabledBy() { return disabledBy; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
