package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.modules.loyalty.api.GainRuleRequestDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Demande d'approbation d'une règle de gain de points (Sprint G.2.3).
 *
 * <p>Workflow :
 * <ol>
 * <li>Restaurateur crée la demande via {@code POST /api/loyalty/gain-rule-requests}
 * avec les paramètres souhaités (status = {@code pending})</li>
 * <li>Admin reçoit notification, examine, puis :
 * <ul>
 * <li>{@code PATCH .../approve} → crée la GainRule (désactivée), met
 * {@code status='approved'} + {@code createdRuleId}</li>
 * <li>{@code PATCH .../reject} → met {@code status='rejected'} + {@code rejectionReason}</li>
 * </ul>
 * </li>
 * <li>Restaurateur active manuellement la règle créée (cas approved).</li>
 * </ol>
 *
 * <p>Audit standard via {@link SoftDeletableAuditedEntity}.
 */
@Entity
@Table(name = "gain_rule_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GainRuleRequest extends SoftDeletableAuditedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    // Auto-fill via trigger V18 depuis restaurants.tenant_id
    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 128)
    @Setter private String name;

    @Column(name = "description", length = 1024)
    @Setter private String description;

    @Column(name = "type", nullable = false, length = 64)
    @Setter private String type = "standard";

    @Column(name = "conversion_rate", nullable = false, precision = 6, scale = 4)
    @Setter private BigDecimal conversionRate = new BigDecimal("0.1000");

    @Column(name = "cap_per_visit")
    @Setter private Integer capPerVisit;

    @Column(name = "cap_per_month")
    @Setter private Integer capPerMonth;

    @Column(name = "min_amount", precision = 10, scale = 2)
    @Setter private BigDecimal minAmount = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 64)
    @Setter private String status = "pending";

    @Column(name = "rejection_reason", length = 1024)
    @Setter private String rejectionReason;

    @Column(name = "reviewed_by")
    @Setter private UUID reviewedById;

    @Column(name = "reviewed_at")
    @Setter private Instant reviewedAt;

    @Column(name = "created_rule_id")
    @Setter private UUID createdRuleId;

    public GainRuleRequest(UUID id, UUID restaurantId, String name, BigDecimal conversionRate) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.name = name;
        this.conversionRate = conversionRate;
    }

    public GainRuleRequestDto toDto() {
        return new GainRuleRequestDto(
            id, restaurantId, getCreatedBy(), name, description, type,
            conversionRate, capPerVisit, capPerMonth, minAmount,
            status, rejectionReason, reviewedById, reviewedAt, createdRuleId,
            getCreatedAt()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        GainRuleRequest that = (GainRuleRequest) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
