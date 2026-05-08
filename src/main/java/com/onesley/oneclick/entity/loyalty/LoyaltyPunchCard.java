package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.entity.shared.PunchCardActivity;
import com.onesley.oneclick.entity.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.loyalty_punch_cards} — cartes de fidélité 10/1 PCC
 * (padel/spa/golf/seminar). Alternative aux points pour activités à séances.
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code tenant_id NOT NULL} → {@link Tenant} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code client_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 * </ul>
 */
@Entity
@Table(name = "loyalty_punch_cards")
public class LoyaltyPunchCard extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "client_id", nullable = false, insertable = false, updatable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Profile client;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @NotNull
    @Column(name = "activity_type", nullable = false, columnDefinition = "punch_card_activity")
    private PunchCardActivity activityType;

    @NotNull
    @Column(name = "count_punched", nullable = false)
    private Integer countPunched;

    @NotNull
    @Column(name = "threshold", nullable = false)
    private Integer threshold;

    @NotNull
    @Column(name = "redeemed_count", nullable = false)
    private Integer redeemedCount;

    @Column(name = "last_punched_at")
    private Instant lastPunchedAt;

    @Column(name = "last_redeemed_at")
    private Instant lastRedeemedAt;

    protected LoyaltyPunchCard() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public UUID getClientId() { return clientId; }
    public Profile getClient() { return client; }
    public void setClient(Profile client) { this.client = client; }
    public PunchCardActivity getActivityType() { return activityType; }
    public Integer getCountPunched() { return countPunched; }
    public Integer getThreshold() { return threshold; }
    public Integer getRedeemedCount() { return redeemedCount; }
    public Instant getLastPunchedAt() { return lastPunchedAt; }
    public Instant getLastRedeemedAt() { return lastRedeemedAt; }

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
        LoyaltyPunchCard that = (LoyaltyPunchCard) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
