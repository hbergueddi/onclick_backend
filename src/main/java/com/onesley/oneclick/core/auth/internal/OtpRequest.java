package com.onesley.oneclick.core.auth.internal;

import com.onesley.oneclick.core.identity.api.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Code OTP — purpose : signup / reset_password / verify_phone / verify_email / 2fa / redemption.
 */
@Entity
@Table(name = "otp_requests")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OtpRequest {

    public static final String PURPOSE_SIGNUP = "signup";
    public static final String PURPOSE_RESET_PASSWORD = "reset_password";
    public static final String PURPOSE_VERIFY_PHONE = "verify_phone";
    public static final String PURPOSE_VERIFY_EMAIL = "verify_email";
    public static final String PURPOSE_2FA = "2fa";
    public static final String PURPOSE_REDEMPTION = "redemption";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, insertable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank
    @Pattern(regexp = "^(signup|reset_password|verify_phone|verify_email|2fa|redemption)$")
    @Column(name = "purpose", nullable = false)
    private String purpose;

    @NotBlank
    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public OtpRequest(UUID id, User user, String purpose, String code, Instant expiresAt) {
        this.id = id;
        this.user = user;
        this.purpose = purpose;
        this.code = code;
        this.expiresAt = expiresAt;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void markVerified() {
        this.verifiedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        OtpRequest that = (OtpRequest) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
