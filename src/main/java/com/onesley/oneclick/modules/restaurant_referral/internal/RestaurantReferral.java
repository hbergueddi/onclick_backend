package com.onesley.oneclick.modules.restaurant_referral.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.restaurant_referral.api.RestaurantReferralDtos.RestaurantReferralDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Parrainage RESTAURANT-à-RESTAURANT (owner → owner) — V100.
 *
 * <p>Le parrain (owner d'un resto existant) possède un code unique. À l'activation par l'owner du
 * resto filleul, le statut passe {@code activated} et la récompense ({@code rewardPoints}) est
 * matérialisée ; le crédit effectif des points est appliqué par {@code loyalty} via l'event
 * {@code RestaurantReferralActivatedEvent} (frontière Modulith).
 *
 * <p>Tous les FK (restos/users/tenant) sont mappés en colonnes {@code UUID} brutes — PAS de
 * {@code @ManyToOne} vers les entités des modules business {@code restaurant}/{@code loyalty}, pour
 * préserver l'invariant Modulith « 0 dépendance business↔business ». L'intégrité FK est garantie
 * par la DB (cf migration V100).
 */
@Entity
@Table(name = "restaurant_referrals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantReferral extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "referrer_restaurant_id", nullable = false, updatable = false)
    private UUID referrerRestaurantId;

    @Column(name = "referrer_user_id", nullable = false, updatable = false)
    private UUID referrerUserId;

    @Column(name = "referral_code", nullable = false, length = 64, updatable = false)
    private String referralCode;

    @Column(name = "referee_restaurant_id")
    private UUID refereeRestaurantId;

    @Column(name = "referee_user_id")
    private UUID refereeUserId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "pending";

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "reward_points", nullable = false)
    private int rewardPoints = 0;

    @Column(name = "rewarded_at")
    private Instant rewardedAt;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Constructeur d'un code de parrainage PENDING (le parrain crée/obtient son code). */
    public RestaurantReferral(UUID id, UUID referrerRestaurantId, UUID referrerUserId,
                              String referralCode, UUID tenantId) {
        this.id = id;
        this.referrerRestaurantId = referrerRestaurantId;
        this.referrerUserId = referrerUserId;
        this.referralCode = referralCode;
        this.tenantId = tenantId;
    }

    /**
     * Active ce parrainage : pose le resto/owner filleul, le statut, la récompense et les
     * horodatages (déclenchement immédiat — la récompense est due dès l'activation).
     */
    public void activate(UUID refereeRestaurantId, UUID refereeUserId, int rewardPoints, Instant now) {
        this.refereeRestaurantId = refereeRestaurantId;
        this.refereeUserId = refereeUserId;
        this.status = "activated";
        this.activatedAt = now;
        this.rewardPoints = rewardPoints;
        this.rewardedAt = now;
    }

    public boolean isActivated() {
        return "activated".equals(status);
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public RestaurantReferralDto toDto() {
        return new RestaurantReferralDto(
            id, referrerRestaurantId, referrerUserId, referralCode,
            refereeRestaurantId, refereeUserId, status, activatedAt,
            rewardPoints, rewardedAt, tenantId, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((RestaurantReferral) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
