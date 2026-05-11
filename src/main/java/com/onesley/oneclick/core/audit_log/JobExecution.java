package com.onesley.oneclick.core.audit_log;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Statut d'exécution d'un job/batch — tracking pour cron, retries, monitoring.
 */
@Entity
@Table(name = "job_executions")
public class JobExecution {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Pattern(regexp = "^(running|success|failed|cancelled)$")
    @Column(name = "status", nullable = false)
    private String status = "running";

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected JobExecution() {
        // JPA
    }

    public JobExecution(UUID id, String jobName) {
        this.id = id;
        this.jobName = jobName;
    }

    public UUID getId() { return id; }
    public String getJobName() { return jobName; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Map<String, Object> getResult() { return result; }
    public String getErrorMessage() { return errorMessage; }

    public void markSuccess(Map<String, Object> result) {
        this.status = "success";
        this.finishedAt = Instant.now();
        this.result = result;
    }

    public void markFailed(String errorMessage) {
        this.status = "failed";
        this.finishedAt = Instant.now();
        this.errorMessage = errorMessage;
    }

    public void markCancelled() {
        this.status = "cancelled";
        this.finishedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        JobExecution that = (JobExecution) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
