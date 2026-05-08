package com.onesley.oneclick.entity.pcc;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.auth.Profile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.pcc_family_members} — relations "Ma Famille" PCC
 * (max 10 proches par membre, suivi historique points fidélité).
 *
 * <h3>Jointures JPA (passe 3) — entité d'association Profile ↔ Profile</h3>
 * <ul>
 *   <li>{@code member_id NOT NULL} → {@link Profile} (le membre PCC propriétaire) en
 *       {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code related_member_id NOT NULL} → {@link Profile} (le proche ajouté) en
 *       {@code @ManyToOne(LAZY)}, optional=false.</li>
 * </ul>
 *
 * <p>Trigger DB enforce max 10 + notification au target. Pas de @OneToMany inverse
 * sur Profile (2 FKs vers Profile, repository custom).
 */
@Entity
@Table(
    name = "pcc_family_members",
    uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "related_member_id"})
)
public class PccFamilyMember extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "member_id", nullable = false, insertable = false, updatable = false)
    private UUID memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Profile member;

    @Column(name = "related_member_id", nullable = false, insertable = false, updatable = false)
    private UUID relatedMemberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "related_member_id", nullable = false)
    private Profile relatedMember;

    @Column(name = "relation")
    private String relation;

    protected PccFamilyMember() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getMemberId() { return memberId; }
    public Profile getMember() { return member; }
    public void setMember(Profile member) { this.member = member; }
    public UUID getRelatedMemberId() { return relatedMemberId; }
    public Profile getRelatedMember() { return relatedMember; }
    public void setRelatedMember(Profile relatedMember) { this.relatedMember = relatedMember; }
    public String getRelation() { return relation; }

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
        PccFamilyMember that = (PccFamilyMember) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
