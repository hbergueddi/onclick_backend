package com.onesley.oneclick.modules.payment;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Moyen de paiement enregistré (carte tokenisée, wallet, etc.). */
@Entity
@Table(name = "payment_methods")
public class PaymentMethod extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Pattern(regexp = "^(card|bank_account|wallet|cash_on_site)$") @Column(name = "type", nullable = false) private String type;
    @Column(name = "last4") private String last4;
    @Column(name = "provider") private String provider;
    @Column(name = "provider_token") private String providerToken;
    @Column(name = "is_default", nullable = false) private boolean isDefault = false;
    @Column(name = "expires_at") private LocalDate expiresAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected PaymentMethod() {}
    public PaymentMethod(UUID id, UUID userId, String type) { this.id = id; this.userId = userId; this.type = type; }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getType() { return type; }
    public String getLast4() { return last4; }
    public void setLast4(String last4) { this.last4 = last4; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderToken() { return providerToken; }
    public void setProviderToken(String providerToken) { this.providerToken = providerToken; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { this.isDefault = aDefault; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDate expiresAt) { this.expiresAt = expiresAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void markDeleted() { this.deletedAt = Instant.now(); }

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
