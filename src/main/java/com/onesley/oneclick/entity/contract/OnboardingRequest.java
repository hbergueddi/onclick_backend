package com.onesley.oneclick.entity.contract;

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
 * Entité {@code public.onboarding_requests} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "onboarding_requests")
public class OnboardingRequest extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "restaurant_name", nullable = false)
    private String restaurantName;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "cuisine")
    private String cuisine;

    @Column(name = "budget")
    private String budget;

    @Column(name = "description")
    private String description;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "ice", nullable = false)
    private String ice;

    @Column(name = "if_number")
    private String ifNumber;

    @Column(name = "rc")
    private String rc;

    @Column(name = "patente")
    private String patente;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "services", nullable = false, columnDefinition = "text[]")
    private List<String> services = new ArrayList<>();

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "tenant_id")
    private UUID tenantId;

    protected OnboardingRequest() {
        // JPA
    }

    public UUID getId() { return id; }
    public String getStatus() { return status; }
    public String getRestaurantName() { return restaurantName; }
    public String getCity() { return city; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getCuisine() { return cuisine; }
    public String getBudget() { return budget; }
    public String getDescription() { return description; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getContactPhone() { return contactPhone; }
    public String getRole() { return role; }
    public String getIce() { return ice; }
    public String getIfNumber() { return ifNumber; }
    public String getRc() { return rc; }
    public String getPatente() { return patente; }
    public Integer getCapacity() { return capacity; }
    public List<String> getServices() { return services; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public UUID getTenantId() { return tenantId; }
}
