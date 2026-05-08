package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.entity.auth.Profile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.ai_usage_bypass} — whitelist d'users qui contournent
 * la limite quotidienne IA.
 *
 * <h3>Pattern : shared primary key 1-1 avec {@link Profile}</h3>
 *
 * <p>PK = {@code user_id} directement (pas de colonne {@code id} séparée).
 * Le {@code @OneToOne @MapsId} dérive le @Id depuis la FK Profile.
 * Cf. pattern identique sur {@code TenantBranding}.
 */
@Entity
@Table(name = "ai_usage_bypass")
@EntityListeners(AuditingEntityListener.class)
public class AiUsageBypass {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // ─── Shared primary key : @MapsId dérive l'@Id depuis la FK Profile ─────
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private Profile user;

    @Column(name = "reason")
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiUsageBypass() {
        // JPA
    }

    public UUID getUserId() { return userId; }
    public Profile getUser() { return user; }
    public void setUser(Profile user) { this.user = user; }
    public String getReason() { return reason; }
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
        AiUsageBypass that = (AiUsageBypass) o;
        return userId != null && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
