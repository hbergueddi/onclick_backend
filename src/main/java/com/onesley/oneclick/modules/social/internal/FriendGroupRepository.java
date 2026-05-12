package com.onesley.oneclick.modules.social.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository {@link FriendGroup} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete obligatoire : filtrer {@code deletedAt IS NULL} explicitement
 * sur tous les finders.
 */
@Repository
public interface FriendGroupRepository extends JpaRepository<FriendGroup, UUID>, JpaSpecificationExecutor<FriendGroup> {

    List<FriendGroup> findAllByOwnerIdAndDeletedAtIsNull(UUID ownerId);

    /**
     * Groupes auxquels un user appartient (via {@code friend_group_members}),
     * excluant les groupes soft-deleted.
     */
    @Query("""
        SELECT g FROM FriendGroup g
        JOIN FriendGroupMember m ON m.friendGroup = g
        WHERE m.friendId = :userId
          AND g.deletedAt IS NULL
        """)
    List<FriendGroup> findAllByMemberUserId(@Param("userId") UUID userId);
}
