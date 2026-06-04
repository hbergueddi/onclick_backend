package com.onesley.oneclick.modules.family.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lien famille unidirectionnel A→B (PCC Lot 5) — le membre {@code memberId} (A) a ajouté
 * le proche {@code relatedMemberId} (B) à sa liste « Ma Famille ». 1 ligne par couple
 * (A, B) — {@code UNIQUE(member_id, related_member_id)}, {@code CHECK(member_id <> related_member_id)}.
 *
 * <p>Table {@code pcc_family_members} (V68). On ne stocke que les 2 ids + la relation
 * textuelle ({@code Conjoint·e / Enfant / Parent / Frère·Sœur / Autre}). Les noms/avatar
 * du proche et ses points fidélité sont résolus à la lecture (UserDirectoryApi + read-view
 * native), jamais dénormalisés ici.
 */
@Entity
@Table(name = "pcc_family_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PccFamilyMember extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** A — le membre propriétaire du lien (le caller qui a ajouté). */
    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    /** B — le proche ajouté. */
    @Column(name = "related_member_id", nullable = false, updatable = false)
    private UUID relatedMemberId;

    /** Relation textuelle libre, fixée à l'ajout (pas d'UPDATE:FAMILY exposé). */
    @Column(name = "relation", length = 64)
    @Setter private String relation;

    public PccFamilyMember(UUID id, UUID memberId, UUID relatedMemberId, String relation) {
        this.id = id;
        this.memberId = memberId;
        this.relatedMemberId = relatedMemberId;
        this.relation = relation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((PccFamilyMember) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
