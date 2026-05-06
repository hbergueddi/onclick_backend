package com.onesley.oneclick.entity.admin;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Entité {@code public.staff_notification_preferences} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "staff_notification_preferences")
public class StaffNotificationPreference extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "booking", nullable = false)
    private Boolean booking;

    @Column(name = "reservation", nullable = false)
    private Boolean reservation;

    @Column(name = "feedback", nullable = false)
    private Boolean feedback;

    @Column(name = "loyalty", nullable = false)
    private Boolean loyalty;

    @Column(name = "system", nullable = false)
    private Boolean system;

    protected StaffNotificationPreference() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Boolean getBooking() { return booking; }
    public Boolean getReservation() { return reservation; }
    public Boolean getFeedback() { return feedback; }
    public Boolean getLoyalty() { return loyalty; }
    public Boolean getSystem() { return system; }
}
