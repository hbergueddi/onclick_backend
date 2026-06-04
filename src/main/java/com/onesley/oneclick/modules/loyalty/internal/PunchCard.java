package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.loyalty.api.PunchCardDto;
import jakarta.persistence.*;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Carte de fidélité « punch card » 10/1 (PCC Lot 3) — 1 carte par
 * (tenant, client, activité). Chaque réservation ressource honorée
 * (booking → {@code completed}) incrémente {@code countPunched} ; à chaque
 * palier de {@code threshold} (défaut 10) atteint, le staff peut appliquer une
 * séance gratuite ({@code redeem} → {@code redeemedCount++}).
 *
 * <p>Table {@code loyalty_punch_cards} (V67). Modèle générique : la colonne
 * {@code activity} porte l'activité dérivée du {@code resource_type} via le
 * mapping de {@link PunchCardService}.
 */
@Entity
@Table(name = "loyalty_punch_cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PunchCard extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "activity", nullable = false, length = 40)
    private String activity;

    @Column(name = "count_punched", nullable = false)
    @Setter private int countPunched = 0;

    @Column(name = "threshold", nullable = false)
    @Setter private int threshold = 10;

    @Column(name = "redeemed_count", nullable = false)
    @Setter private int redeemedCount = 0;

    @Column(name = "last_punched_at")
    @Setter private Instant lastPunchedAt;

    @Column(name = "last_redeemed_at")
    @Setter private Instant lastRedeemedAt;

    public PunchCard(UUID id, UUID tenantId, UUID clientId, String activity) {
        this.id = id;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.activity = activity;
    }

    /**
     * Punches restant avant la prochaine séance gratuite, dans le palier courant.
     * <p>{@code threshold - (countPunched - redeemedCount*threshold)} — borné à
     * {@code [0, threshold]} pour rester lisible si {@code countPunched} déborde un palier
     * non encore redeemed.</p>
     */
    public int remaining() {
        int withinTier = countPunched - redeemedCount * threshold;
        int rem = threshold - withinTier;
        if (rem < 0) return 0;
        if (rem > threshold) return threshold;
        return rem;
    }

    /** true s'il y a au moins un palier complet disponible à redeem. */
    public boolean hasRedeemablePalier() {
        return (countPunched - redeemedCount * threshold) >= threshold;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public PunchCardDto toDto() {
        return new PunchCardDto(id, activity, countPunched, threshold, redeemedCount, remaining(), lastPunchedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((PunchCard) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
