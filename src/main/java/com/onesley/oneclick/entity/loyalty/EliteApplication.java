package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.auth.Profile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.elite_applications} — candidatures Elite Club avec
 * questionnaire, motivation, sponsor, review admin.
 *
 * <h3>Jointures JPA (passe 3)</h3>
 * <ul>
 *   <li>{@code user_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code sponsor_user_id} → {@link Profile} en {@code @ManyToOne(LAZY)}, nullable
 *       (auto-application sans sponsor possible).</li>
 *   <li>{@code reviewed_by} : audit field, reste UUID brut.</li>
 * </ul>
 */
@Entity
@Table(name = "elite_applications")
public class EliteApplication extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, insertable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Profile user;

    @NotBlank
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Email
    @NotBlank
    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @NotBlank
    @Column(name = "motivation", nullable = false)
    private String motivation;

    @NotBlank
    @Column(name = "preferred_tier", nullable = false)
    private String preferredTier;

    @Column(name = "sponsor_user_id", insertable = false, updatable = false)
    private UUID sponsorUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sponsor_user_id")
    private Profile sponsorUser;

    @Column(name = "sponsor_name")
    private String sponsorName;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "admin_note")
    private String adminNote;

    /** Audit field : UUID brut. */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "age")
    private Integer age;

    @Column(name = "profession")
    private String profession;

    @Column(name = "company")
    private String company;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "interests", columnDefinition = "text[]")
    private List<String> interests = new ArrayList<>();

    @Column(name = "has_other_clubs")
    private Boolean hasOtherClubs;

    @Column(name = "other_clubs_details")
    private String otherClubsDetails;

    @Column(name = "city")
    private String city;

    @Column(name = "annual_dining_budget")
    private String annualDiningBudget;

    @Column(name = "referral_source")
    private String referralSource;

    protected EliteApplication() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Profile getUser() { return user; }
    public void setUser(Profile user) { this.user = user; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getMotivation() { return motivation; }
    public String getPreferredTier() { return preferredTier; }
    public UUID getSponsorUserId() { return sponsorUserId; }
    public Profile getSponsorUser() { return sponsorUser; }
    public void setSponsorUser(Profile sponsorUser) { this.sponsorUser = sponsorUser; }
    public String getSponsorName() { return sponsorName; }
    public String getStatus() { return status; }
    public String getAdminNote() { return adminNote; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Integer getAge() { return age; }
    public String getProfession() { return profession; }
    public String getCompany() { return company; }
    public List<String> getInterests() { return interests; }
    public Boolean getHasOtherClubs() { return hasOtherClubs; }
    public String getOtherClubsDetails() { return otherClubsDetails; }
    public String getCity() { return city; }
    public String getAnnualDiningBudget() { return annualDiningBudget; }
    public String getReferralSource() { return referralSource; }

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
        EliteApplication that = (EliteApplication) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
