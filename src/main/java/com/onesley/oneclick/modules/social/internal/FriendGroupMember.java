package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.social.api.SocialDtos.FriendGroupMemberDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Junction {@code users × friend_groups} — appartenance + rôle dans le groupe.
 *
 * <p>{@code role} ∈ {@code (owner, admin, member)}. UNIQUE {@code (friend_group_id, friend_id)}.
 * {@code joinedAt} renseigné par {@link CreatedDate} (mappé sur {@code joined_at}).
 */
@Entity
@Table(
    name = "friend_group_members",
    uniqueConstraints = @UniqueConstraint(
        name = "friend_group_members_unique",
        columnNames = {"friend_group_id", "friend_id"}
    )
)
@EntityListeners(AuditingEntityListener.class)
public class FriendGroupMember {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;

    @Column(name = "friend_group_id", nullable = false, insertable = false, updatable = false) private UUID friendGroupId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "friend_group_id", nullable = false)
    private FriendGroup friendGroup;

    @Column(name = "friend_id", nullable = false, insertable = false, updatable = false) private UUID friendId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "friend_id", nullable = false)
    private User friend;

    @Pattern(regexp = "^(owner|admin|member)$")
    @Column(name = "role", nullable = false)
    private String role = "member";

    @CreatedDate
    @Column(name = "joined_at", updatable = false, nullable = false)
    private Instant joinedAt;

    @Column(name = "invited_by") private UUID invitedBy;

    protected FriendGroupMember() {}

    public FriendGroupMember(UUID id, FriendGroup friendGroup, User friend, String role) {
        this.id = id;
        this.friendGroup = friendGroup;
        this.friend = friend;
        this.role = role == null ? "member" : role;
    }

    public UUID getId() { return id; }
    public UUID getFriendGroupId() { return friendGroupId; }
    public FriendGroup getFriendGroup() { return friendGroup; }
    public UUID getFriendId() { return friendId; }
    public User getFriend() { return friend; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Instant getJoinedAt() { return joinedAt; }
    public UUID getInvitedBy() { return invitedBy; }
    public void setInvitedBy(UUID invitedBy) { this.invitedBy = invitedBy; }

    /** Mapping vers le DTO public exposé hors du module. */
    public FriendGroupMemberDto toDto() {
        return new FriendGroupMemberDto(id, friendGroupId, friendId, role, joinedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((FriendGroupMember) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
