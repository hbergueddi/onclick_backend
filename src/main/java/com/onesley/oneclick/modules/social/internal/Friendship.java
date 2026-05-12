package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.social.api.SocialDtos.FriendshipDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/** Amitié bidirectionnelle — 1 row par couple (user1 < user2 par convention). */
@Entity
@Table(name = "friendships", uniqueConstraints = @UniqueConstraint(columnNames = {"user1_id", "user2_id"}))
public class Friendship extends TimestampedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;
    @Column(name = "user1_id", nullable = false, insertable = false, updatable = false) private UUID user1Id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user1_id", nullable = false) private User user1;
    @Column(name = "user2_id", nullable = false, insertable = false, updatable = false) private UUID user2Id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user2_id", nullable = false) private User user2;
    @Pattern(regexp = "^(pending|accepted|declined|blocked)$") @Column(name = "status", nullable = false) private String status = "pending";
    @Column(name = "accepted_at") private Instant acceptedAt;

    protected Friendship() {}
    public Friendship(UUID id, User user1, User user2) {
        // Convention canonique : user1.id < user2.id pour éviter doublons.
        if (user1.getId().compareTo(user2.getId()) < 0) { this.user1 = user1; this.user2 = user2; }
        else { this.user1 = user2; this.user2 = user1; }
        this.id = id;
    }

    public UUID getId() { return id; }
    public UUID getUser1Id() { return user1Id; }
    public User getUser1() { return user1; }
    public UUID getUser2Id() { return user2Id; }
    public User getUser2() { return user2; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void markAccepted() { this.acceptedAt = Instant.now(); this.status = "accepted"; }

    /** Mapping vers le DTO public exposé hors du module. */
    public FriendshipDto toDto() {
        return new FriendshipDto(id, user1Id, user2Id, status, acceptedAt, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((Friendship) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
