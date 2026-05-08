package com.onesley.oneclick.entity.auth;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.restaurant.RestaurantGroup;
import com.onesley.oneclick.entity.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.profiles} — informations applicatives du user (étend
 * {@code auth.users} via la même PK UUID).
 *
 * <p>Pattern : <i>UUID PK simple (1-1 avec auth.users) + héritage
 * {@link TimestampedEntity} + ARRAY text + numeric précis + jointures multi-tenant</i>.
 *
 * <p>Mapping notable :
 * <ul>
 *   <li>{@code id uuid} = même UUID que {@code auth.users.id} (FK 1-1) — l'ID
 *       est fourni par le flow de signup, pas généré par Hibernate.</li>
 *   <li>{@code allergens text[]} → {@code List<String>} via
 *       {@code @JdbcTypeCode(SqlTypes.ARRAY)} (Hibernate 6+ natif).</li>
 *   <li>{@code reliability_score numeric(3,1)} → {@link BigDecimal} (jamais
 *       {@code double}/{@code float} pour des nombres précis).</li>
 *   <li>{@code created_at} + {@code updated_at} hérités de {@link TimestampedEntity}.</li>
 * </ul>
 *
 * <h3>Jointures JPA (passe 3 — relations matérialisées)</h3>
 * <ul>
 *   <li>{@code tenant_id} → {@link Tenant} en {@code @ManyToOne(LAZY)}, nullable
 *       (les profils OneClick standard l'ont = {@code 'oneclick'} UUID, mais
 *       certains seedés legacy ont NULL).</li>
 *   <li>{@code tenant_group_id} → {@link RestaurantGroup} en {@code @ManyToOne(LAZY)},
 *       nullable. <b>Legacy alias</b> de {@code group_id} introduit avant la
 *       refonte multi-tenant Session 31. À déprécier en V2 au profit de la chaîne
 *       {@code profile.tenant.group}. Conservé pour anti-régression.</li>
 *   <li>FK 1-1 vers {@code auth.users} : non matérialisée (auth.users est un
 *       schéma système Supabase, on ne crée pas d'entité dessus).</li>
 * </ul>
 *
 * <p>Convention senior : les UUID raccourcis ({@code tenantId}, {@code tenantGroupId})
 * restent accessibles via getter pour les DTOs/projections qui n'ont pas besoin
 * de l'objet complet (évite le LAZY load). Ils sont {@code insertable=false,
 * updatable=false} côté ORM — l'écriture passe exclusivement par {@link #setTenant}
 * / {@link #setTenantGroup} pour éviter la double source de vérité.
 *
 * <p>{@code equals}/{@code hashCode} : pattern anti-proxy LAZY (cf. Vlad Mihalcea).
 * Comparer un proxy LAZY à une entité hydratée doit retourner {@code true} si même {@code id}.
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

    // ─── Jointure tenant_group_id (legacy whitelabel alias) ─────────────────
    @Column(name = "tenant_group_id", insertable = false, updatable = false)
    private UUID tenantGroupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_group_id")
    private RestaurantGroup tenantGroup;

    // ─── Jointure tenant_id (multi-tenant whitelabel) ───────────────────────
    @Column(name = "tenant_id", insertable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

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

    /** Raccourci read-only (issu de la colonne FK). Utiliser pour DTOs/projections. */
    public UUID getTenantGroupId() {
        return tenantGroupId;
    }

    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public RestaurantGroup getTenantGroup() {
        return tenantGroup;
    }

    public void setTenantGroup(RestaurantGroup tenantGroup) {
        this.tenantGroup = tenantGroup;
    }

    /** Raccourci read-only (issu de la colonne FK). Utiliser pour DTOs/projections. */
    public UUID getTenantId() {
        return tenantId;
    }

    /** Lazy load — ne pas appeler hors {@code @Transactional} si proxy non hydraté. */
    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
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

    // ─── equals / hashCode anti-proxy LAZY ──────────────────────────────────
    // Comparer un HibernateProxy à une entité hydratée doit retourner true si
    // même id (sinon Set<Profile> retient 2 entrées pour le même profil).

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
        Profile that = (Profile) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        // Stable indépendamment de l'état proxy/hydraté + id null/non-null.
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
