package com.onesley.oneclick.entity.admin;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.auth.Profile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.staff_notification_preferences} — préférences notifs
 * staff (par catégorie : booking, reservation, feedback, loyalty, system).
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code user_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 * </ul>
 */
@Entity
@Table(name = "staff_notification_preferences")
public class StaffNotificationPreference extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, insertable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Profile user;

    @NotNull
    @Column(name = "booking", nullable = false)
    private Boolean booking;

    @NotNull
    @Column(name = "reservation", nullable = false)
    private Boolean reservation;

    @NotNull
    @Column(name = "feedback", nullable = false)
    private Boolean feedback;

    @NotNull
    @Column(name = "loyalty", nullable = false)
    private Boolean loyalty;

    @NotNull
    @Column(name = "system", nullable = false)
    private Boolean system;

    protected StaffNotificationPreference() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Profile getUser() { return user; }
    public void setUser(Profile user) { this.user = user; }
    public Boolean getBooking() { return booking; }
    public Boolean getReservation() { return reservation; }
    public Boolean getFeedback() { return feedback; }
    public Boolean getLoyalty() { return loyalty; }
    public Boolean getSystem() { return system; }

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
        StaffNotificationPreference that = (StaffNotificationPreference) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
