package com.onesley.oneclick.entity.reservation;

import com.onesley.oneclick.entity.auth.Profile;
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
 * Entité {@code public.friend_group_members} — membres d'un squad
 * (entité d'association FriendGroup ↔ Profile, PK simple).
 *
 * <h3>Jointures JPA (passe 3) — aggregate member de {@link FriendGroup}</h3>
 * <ul>
 *   <li>{@code group_id NOT NULL} → {@link FriendGroup} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code friend_id NOT NULL} → {@link Profile} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 * </ul>
 */
@Entity
@Table(name = "friend_group_members")
@EntityListeners(AuditingEntityListener.class)
public class FriendGroupMember {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false, insertable = false, updatable = false)
    private UUID groupId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private FriendGroup group;

    @Column(name = "friend_id", nullable = false, insertable = false, updatable = false)
    private UUID friendId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "friend_id", nullable = false)
    private Profile friend;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FriendGroupMember() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getGroupId() { return groupId; }
    public FriendGroup getGroup() { return group; }
    /** Package-private : appelé par les helpers {@link FriendGroup#addMember}/{@code removeMember}. */
    void setGroup(FriendGroup group) { this.group = group; }

    public UUID getFriendId() { return friendId; }
    public Profile getFriend() { return friend; }
    public void setFriend(Profile friend) { this.friend = friend; }

    public Instant getCreatedAt() { return createdAt; }

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
        FriendGroupMember that = (FriendGroupMember) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
