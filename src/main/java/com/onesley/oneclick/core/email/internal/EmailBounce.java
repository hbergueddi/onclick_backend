package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bounce email Resend (Gap #4). 1 ligne par adresse (UNIQUE email, lowercase),
 * alimentée par le webhook Resend et consultée avant chaque envoi (suppression).
 *
 * <p>{@code is_suppressed} = true pour un bounce {@code permanent} ou une
 * {@code complaint} → tout futur envoi vers cette adresse est sauté.
 */
@Entity
@Table(name = "email_bounces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailBounce extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, updatable = false)
    private String email;

    @Column(name = "bounce_type", nullable = false)
    @Setter private String bounceType;

    @Column(name = "bounce_reason")
    @Setter private String bounceReason;

    @Column(name = "is_suppressed", nullable = false)
    @Setter private boolean suppressed = false;

    @Column(name = "last_bounced_at", nullable = false)
    @Setter private Instant lastBouncedAt = Instant.now();

    @Column(name = "bounce_count", nullable = false)
    @Setter private int bounceCount = 1;

    @Column(name = "source_ef")
    @Setter private String sourceEf;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_event", columnDefinition = "jsonb")
    @Setter private String rawEvent;

    public EmailBounce(UUID id, String email, String bounceType) {
        this.id = id;
        this.email = email;
        this.bounceType = bounceType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        EmailBounce that = (EmailBounce) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
