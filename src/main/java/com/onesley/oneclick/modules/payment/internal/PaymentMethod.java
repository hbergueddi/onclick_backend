package com.onesley.oneclick.modules.payment.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.PaymentMethodDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Moyen de paiement enregistré (carte tokenisée, wallet, etc.). */
@Entity
@Table(name = "payment_methods")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentMethod extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Pattern(regexp = "^(card|bank_account|wallet|cash_on_site)$") @Column(name = "type", nullable = false) private String type;
    @Column(name = "last4") @Setter private String last4;
    @Column(name = "provider") @Setter private String provider;
    @Column(name = "provider_token") @Setter private String providerToken;
    @Column(name = "is_default", nullable = false) @Setter private boolean isDefault = false;
    @Column(name = "expires_at") @Setter private LocalDate expiresAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    public PaymentMethod(UUID id, UUID userId, String type) { this.id = id; this.userId = userId; this.type = type; }
    public void markDeleted() { this.deletedAt = Instant.now(); }

    /** Mapping vers le DTO public exposé hors du module. */
    public PaymentMethodDto toDto() {
        return new PaymentMethodDto(id, userId, type, last4, provider, isDefault, expiresAt, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((PaymentMethod) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
