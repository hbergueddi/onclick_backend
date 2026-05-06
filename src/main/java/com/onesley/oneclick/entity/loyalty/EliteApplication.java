package com.onesley.oneclick.entity.loyalty;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité {@code public.elite_applications} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "elite_applications")
public class EliteApplication extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "motivation", nullable = false)
    private String motivation;

    @Column(name = "preferred_tier", nullable = false)
    private String preferredTier;

    @Column(name = "sponsor_user_id")
    private UUID sponsorUserId;

    @Column(name = "sponsor_name")
    private String sponsorName;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "admin_note")
    private String adminNote;

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
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getMotivation() { return motivation; }
    public String getPreferredTier() { return preferredTier; }
    public UUID getSponsorUserId() { return sponsorUserId; }
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
}
