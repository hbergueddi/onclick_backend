package com.onesley.oneclick.entity.auth;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entité {@code public.profiles} — informations applicatives du user (étend
 * {@code auth.users} via la même PK UUID).
 *
 * <p>Pattern pilote : <i>UUID PK simple + héritage {@link TimestampedEntity} +
 * ARRAY text + numeric précis</i>.
 *
 * <p>Mapping notable :
 * <ul>
 *   <li>{@code id uuid} = même UUID que {@code auth.users.id} (FK 1-1) — l'ID
 *       est fourni par le flow de signup, pas généré par Hibernate</li>
 *   <li>{@code allergens text[]} → {@code List<String>} via
 *       {@code @JdbcTypeCode(SqlTypes.ARRAY)} (Hibernate 6+ natif)</li>
 *   <li>{@code reliability_score numeric(3,1)} → {@link BigDecimal} (jamais
 *       {@code double}/{@code float} pour des nombres précis)</li>
 *   <li>{@code created_at} + {@code updated_at} hérités de {@link TimestampedEntity}</li>
 *   <li>{@code tenant_id} + {@code tenant_group_id} = FKs gardées comme UUID brut
 *       (pas de @ManyToOne dans le pilote — relations matérialisées au cas par cas
 *       quand le besoin métier est clair). Limite la fan-out au scaffolder bulk.</li>
 *   <li>Trigger DB {@code trg_profiles_updated_at} met à jour {@code updated_at}
 *       côté serveur ; Hibernate fait pareil côté Java. Pas de conflit (Hibernate
 *       envoie sa valeur, le trigger l'écrase, le SELECT post-commit relit la valeur
 *       finale grâce au refresh automatique).</li>
 * </ul>
 *
 * <p>FK 1-1 vers {@code auth.users} : non matérialisée (auth.users est un schéma
 * système Supabase, on ne crée pas d'entité dessus dans le pilote).
 */
@Entity
@Table(name = "profiles")
public class Profile extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName = "";

    @Column(name = "last_name", nullable = false)
    private String lastName = "";

    @Column(name = "phone")
    private String phone;

    @Column(name = "city")
    private String city;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "referral_code", unique = true)
    private String referralCode;

    @Column(name = "reliability_score", precision = 3, scale = 1)
    private BigDecimal reliabilityScore;

    @Column(name = "score_updated_at")
    private Instant scoreUpdatedAt;

    @Column(name = "email")
    private String email;

    @Column(name = "tenant_group_id")
    private UUID tenantGroupId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "community_cover_url")
    private String communityCoverUrl;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allergens", nullable = false, columnDefinition = "text[]")
    private List<String> allergens = new ArrayList<>();

    @Column(name = "language")
    private String language;

    protected Profile() {
        // JPA
    }

    public Profile(UUID id, String firstName, String lastName) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getReferralCode() {
        return referralCode;
    }

    public BigDecimal getReliabilityScore() {
        return reliabilityScore;
    }

    public Instant getScoreUpdatedAt() {
        return scoreUpdatedAt;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UUID getTenantGroupId() {
        return tenantGroupId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getCommunityCoverUrl() {
        return communityCoverUrl;
    }

    public void setCommunityCoverUrl(String communityCoverUrl) {
        this.communityCoverUrl = communityCoverUrl;
    }

    public List<String> getAllergens() {
        return allergens;
    }

    public void setAllergens(List<String> allergens) {
        this.allergens = allergens != null ? allergens : new ArrayList<>();
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
