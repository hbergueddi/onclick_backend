package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.modules.reservation.api.NoShowDisputeDtos.NoShowDisputeDto;
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
import lombok.Setter;

/**
 * Contestation d'un no_show par un client (Feature #3 — domaine dispute du module
 * reservation).
 *
 * <p>Refs par <b>UUID plats</b> ({@code reservationId}, {@code clientId},
 * {@code restaurantId}) — pas de {@code @ManyToOne} cross-module : le module
 * reservation est CLOSED, on ne tient pas d'entités {@code User}/{@code Restaurant}
 * (qui vivent dans d'autres modules). Même discipline que {@code reservation_guests}.
 *
 * <h3>Cycle de vie</h3>
 * <ul>
 *   <li>{@code status} : {@code pending → accepted | refused}</li>
 *   <li>{@code escalationPhase} : {@code resto} (0-1h depuis {@code no_show_marked_at})
 *       puis {@code support} (1-48h) — figé à la création selon la fenêtre courante.</li>
 *   <li>{@code accepted} → la pénalité no_show est reversée (event loyalty).</li>
 * </ul>
 */
@Entity
@Table(name = "no_show_disputes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoShowDispute {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "client_id", nullable = false, updatable = false)
    private UUID clientId;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "status", nullable = false, length = 32)
    @Setter private String status = "pending";

    @Column(name = "escalation_phase", nullable = false, length = 32)
    @Setter private String escalationPhase = "resto";

    @Column(name = "reason", nullable = false, length = 2048)
    @Setter private String reason;

    @Column(name = "photo_url", length = 2048)
    @Setter private String photoUrl;

    @Column(name = "resolution_note", length = 2048)
    @Setter private String resolutionNote;

    @Column(name = "resolved_by")
    @Setter private UUID resolvedBy;

    @Column(name = "resolved_at")
    @Setter private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public NoShowDispute(UUID id, UUID reservationId, UUID clientId, UUID restaurantId,
                         String escalationPhase, String reason, String photoUrl) {
        this.id = id;
        this.reservationId = reservationId;
        this.clientId = clientId;
        this.restaurantId = restaurantId;
        this.escalationPhase = escalationPhase;
        this.reason = reason;
        this.photoUrl = photoUrl;
    }

    /**
     * Mapping vers le DTO public exposé hors du module, <b>sans</b> contexte d'affichage
     * (noms / horodatage résa à null). Utilisé quand l'enrichissement n'est pas requis
     * (ex. réponse de création/résolution où le front a déjà le contexte de la résa).
     */
    public NoShowDisputeDto toDto() {
        return toDto(null, null, null);
    }

    /**
     * Mapping vers le DTO public enrichi du contexte d'affichage résolu côté service
     * ({@code clientName} = "Prénom Nom", {@code restaurantName}, {@code reservationDateTime}).
     * L'entité reste « dumb » : la résolution (UserDirectoryApi + read-view native) est faite
     * en amont par {@code NoShowDisputeService} et injectée ici — pas de couplage cross-module
     * dans l'entité.
     */
    public NoShowDisputeDto toDto(String clientName, String restaurantName, Instant reservationDateTime) {
        return new NoShowDisputeDto(
            id, reservationId, clientId, restaurantId, status, escalationPhase,
            reason, photoUrl, resolutionNote, resolvedBy, resolvedAt, createdAt,
            clientName, restaurantName, reservationDateTime);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        NoShowDispute that = (NoShowDispute) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
