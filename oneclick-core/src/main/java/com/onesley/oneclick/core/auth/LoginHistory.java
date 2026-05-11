package com.onesley.oneclick.core.auth;

import com.onesley.oneclick.core.identity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Historique des tentatives de connexion — audit sécurité (anti brute-force, anomalies).
 *
 * <p>{@code user_id} est nullable : permet de tracker les tentatives sur emails inexistants
 * (utile pour détecter du scanning).
 */
@Entity
@Table(name = "login_histories")
@EntityListeners(AuditingEntityListener.class)
public class LoginHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "device")
    private String device;

    @Column(name = "success", nullable = false)
    private boolean success;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected LoginHistory() {
        // JPA
    }

    public LoginHistory(UUID id, User user, String ipAddress, String device, boolean success) {
        this.id = id;
        this.user = user;
        this.ipAddress = ipAddress;
        this.device = device;
        this.success = success;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public User getUser() { return user; }
    public String getIpAddress() { return ipAddress; }
    public String getDevice() { return device; }
    public boolean isSuccess() { return success; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        LoginHistory that = (LoginHistory) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
