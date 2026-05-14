package com.onesley.oneclick.modules.system.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/** Repository {@link CustomRole} — listing trié par nom. */
public interface CustomRoleRepository extends JpaRepository<CustomRole, UUID> {

    @Query("SELECT r FROM CustomRole r ORDER BY r.name ASC")
    List<CustomRole> findAllOrdered();
}
