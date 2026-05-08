package com.onesley.oneclick.entity.reservation;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.auth.Profile;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.BatchSize;
import org.hibernate.proxy.HibernateProxy;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Entité {@code public.friend_groups} — squads (groupes d'amis privés)
 * pour invitations groupées aux réservations.
 *
 * <h3>Jointures JPA (passe 3) — aggregate root pour {@link FriendGroupMember}</h3>
 * <ul>
 *   <li>{@code owner_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code @OneToMany members} : aggregate fort, cascade ALL + orphanRemoval +
 *       {@code @BatchSize(50)} (volume borné ~10 membres/squad).</li>
 * </ul>
 */
@Entity
@Table(name = "friend_groups")
public class FriendGroup extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, insertable = false, updatable = false)
    private UUID ownerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Profile owner;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "emoji")
    private String emoji;

    // ─── Aggregate member : members (cascade ALL + orphanRemoval) ───────────
    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private Set<FriendGroupMember> members = new HashSet<>();

    protected FriendGroup() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public Profile getOwner() { return owner; }
    public void setOwner(Profile owner) { this.owner = owner; }
    public String getName() { return name; }
    public String getEmoji() { return emoji; }
    public Set<FriendGroupMember> getMembers() { return members; }

    public void addMember(FriendGroupMember m) {
        members.add(m);
        m.setGroup(this);
    }

    public void removeMember(FriendGroupMember m) {
        members.remove(m);
        m.setGroup(null);
    }

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
        FriendGroup that = (FriendGroup) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
