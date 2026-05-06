package com.onesley.oneclick.entity.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entité {@code public.friend_group_members} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at sans updated_at, inline.
 */
@Entity
@Table(name = "friend_group_members")
@EntityListeners(AuditingEntityListener.class)
public class FriendGroupMember {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @NotNull
    @Column(name = "friend_id", nullable = false)
    private UUID friendId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FriendGroupMember() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getGroupId() { return groupId; }
    public UUID getFriendId() { return friendId; }
    public Instant getCreatedAt() { return createdAt; }
}
