package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.core.identity.User;
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
import jakarta.validation.constraints.Pattern;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Mouvement de points — typé pour expliquer la modification de balance.
 *
 * <p>Types :
 * <ul>
 *   <li>{@code earn} — gagné via scan ticket (points > 0)</li>
 *   <li>{@code spend} — utilisé en redemption (points < 0)</li>
 *   <li>{@code expire} — expiré (points < 0)</li>
 *   <li>{@code gift} — transféré depuis un autre user (points > 0 ou < 0 selon sens)</li>
 *   <li>{@code adjust} — ajustement manuel admin</li>
 * </ul>
 */
@Entity
@Table(name = "loyalty_transactions")
@EntityListeners(AuditingEntityListener.class)
public class LoyaltyTransaction {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, insertable = false, updatable = false)
    private UUID accountId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private LoyaltyAccount account;

    @NotBlank
    @Pattern(regexp = "^(earn|spend|expire|gift|adjust)$")
    @Column(name = "type", nullable = false)
    private String type;

    @NotNull
    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason")
    private String reason;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    private User createdBy;

    protected LoyaltyTransaction() {
        // JPA
    }

    public LoyaltyTransaction(UUID id, LoyaltyAccount account, String type, Integer points, String reason) {
        this.id = id;
        this.account = account;
        this.type = type;
        this.points = points;
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public LoyaltyAccount getAccount() { return account; }
    public String getType() { return type; }
    public Integer getPoints() { return points; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedById() { return createdById; }
    public User getCreatedBy() { return createdBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        LoyaltyTransaction that = (LoyaltyTransaction) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
