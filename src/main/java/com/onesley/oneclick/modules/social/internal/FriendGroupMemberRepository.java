package com.onesley.oneclick.modules.social.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository {@link FriendGroupMember} — accès CRUD + finders dérivés.
 *
 * <p>Pas de soft delete (junction physique) — DELETE direct quand un membre est retiré.
 */
@Repository
public interface FriendGroupMemberRepository extends JpaRepository<FriendGroupMember, UUID>, JpaSpecificationExecutor<FriendGroupMember> {

    List<FriendGroupMember> findAllByFriendGroupId(UUID friendGroupId);

    boolean existsByFriendGroupIdAndFriendId(UUID friendGroupId, UUID friendId);

    long countByFriendGroupId(UUID friendGroupId);

    @Modifying
    @Query("DELETE FROM FriendGroupMember m WHERE m.friendGroupId = :groupId AND m.friendId = :friendId")
    int deleteByFriendGroupIdAndFriendId(@Param("groupId") UUID groupId, @Param("friendId") UUID friendId);
}
