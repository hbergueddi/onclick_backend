package com.onesley.oneclick.core.audit_log.internal;

import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.ErrorLogDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Erreurs applicatives — log local centralisé. Équivalent persisté de Sentry.
 */
@Entity
@Table(name = "error_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ErrorLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @NotBlank
    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "stacktrace", columnDefinition = "text")
    @Setter private String stacktrace;

    @Pattern(regexp = "^(debug|info|warn|error|fatal)$")
    @Column(name = "severity", nullable = false)
    private String severity = "error";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public ErrorLog(UUID id, String serviceName, String message, String severity) {
        this.id = id;
        this.serviceName = serviceName;
        this.message = message;
        this.severity = severity;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public ErrorLogDto toDto() {
        return new ErrorLogDto(id, serviceName, message, stacktrace, severity, metadata, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        ErrorLog that = (ErrorLog) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
