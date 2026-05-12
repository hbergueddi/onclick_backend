package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.social.api.SocialDtos.ReferralDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/** Parrainage avec code et status d'activation. */
@Entity
@Table(name = "referrals")
public class Referral extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "referrer_id", nullable = false, insertable = false, updatable = false) private UUID referrerId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "referrer_id", nullable = false) private User referrer;
    @Column(name = "referred_user_id", insertable = false, updatable = false) private UUID referredUserId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "referred_user_id") private User referredUser;
    @NotBlank @Column(name = "referral_code", nullable = false) private String referralCode;
    @Pattern(regexp = "^(pending|activated|expired)$") @Column(name = "status", nullable = false) private String status = "pending";
    @Column(name = "activated_at") private Instant activatedAt;

    protected Referral() {}
    public Referral(UUID id, User referrer, String referralCode) {
        this.id = id; this.referrer = referrer; this.referralCode = referralCode;
    }

    public UUID getId() { return id; }
    public UUID getReferrerId() { return referrerId; }
    public User getReferrer() { return referrer; }
    public UUID getReferredUserId() { return referredUserId; }
    public User getReferredUser() { return referredUser; }
    public void setReferredUser(User referredUser) { this.referredUser = referredUser; }
    public String getReferralCode() { return referralCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getActivatedAt() { return activatedAt; }
    public void markActivated() { this.activatedAt = Instant.now(); this.status = "activated"; }

    /** Mapping vers le DTO public exposé hors du module. */
    public ReferralDto toDto() {
        return new ReferralDto(id, referrerId, referredUserId, referralCode, status, activatedAt, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((Referral) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
