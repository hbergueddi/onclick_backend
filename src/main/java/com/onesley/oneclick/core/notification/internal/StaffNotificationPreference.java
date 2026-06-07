package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.notification.api.NotificationDtos.StaffNotificationPrefsDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Préférences de notifications d'un utilisateur (Gap #5 — port legacy B.5).
 *
 * <p>1 row par user (colonne {@code user_id} UNIQUE). 5 toggles correspondant aux
 * catégories de la page StaffNotifications. Tous {@code true} par défaut (opt-out).
 *
 * <p>Microservice pattern (cohérent avec {@link DeviceToken}) : pas de FK JPA vers
 * {@code User} ; la cohérence est assurée par la FK Postgres {@code user_id → users(id)}.
 */
@Entity
@Table(name = "staff_notification_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffNotificationPreference extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "booking", nullable = false)
    @Setter private boolean booking = true;

    @Column(name = "reservation", nullable = false)
    @Setter private boolean reservation = true;

    @Column(name = "feedback", nullable = false)
    @Setter private boolean feedback = true;

    @Column(name = "loyalty", nullable = false)
    @Setter private boolean loyalty = true;

    @Column(name = "system", nullable = false)
    @Setter private boolean system = true;

    public StaffNotificationPreference(UUID id, UUID userId) {
        this.id = id;
        this.userId = userId;
    }

    /** Applique les 5 toggles d'un coup (upsert depuis le DTO). */
    public void apply(StaffNotificationPrefsDto dto) {
        this.booking = dto.booking();
        this.reservation = dto.reservation();
        this.feedback = dto.feedback();
        this.loyalty = dto.loyalty();
        this.system = dto.system();
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public StaffNotificationPrefsDto toDto() {
        return new StaffNotificationPrefsDto(booking, reservation, feedback, loyalty, system);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        StaffNotificationPreference that = (StaffNotificationPreference) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
