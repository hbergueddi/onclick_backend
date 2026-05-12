package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.audit.SoftDeletableAuditedEntity;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.modules.social.api.SocialDtos.FriendGroupDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Groupe d'amis (squad/team) — réservation collective Pocket.
 *
 * <p>Étend {@link SoftDeletableAuditedEntity} : soft delete via {@code deleted_at},
 * {@code created_by}/{@code updated_by} renseignés via {@code SecurityContextAuditorAware}.
 *
 * <p>Le {@code memberCount} affiché dans le DTO est calculé côté service (LEFT JOIN
 * sur {@code friend_group_members}) — pas stocké pour éviter la dérive.
 */
@Entity
@Table(name = "friend_groups")
public class FriendGroup extends SoftDeletableAuditedEntity {

    @Id @Column(name = "id", nullable = false, updatable = false) private UUID id;

    @Column(name = "owner_id", nullable = false, insertable = false, updatable = false) private UUID ownerId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @NotBlank @Size(max = 500) @Column(name = "name", nullable = false) private String name;
    @Column(name = "description") private String description;
    @Column(name = "avatar_url") private String avatarUrl;

    protected FriendGroup() {}

    public FriendGroup(UUID id, User owner, String name) {
        this.id = id;
        this.owner = owner;
        this.name = name;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public User getOwner() { return owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    /**
     * Mapping vers le DTO public — {@code memberCount} fourni par le service
     * (pas calculable au niveau Entity sans requête supplémentaire).
     */
    public FriendGroupDto toDto(long memberCount) {
        return new FriendGroupDto(id, ownerId, name, description, avatarUrl, memberCount, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && java.util.Objects.equals(id, ((FriendGroup) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof org.hibernate.proxy.HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
