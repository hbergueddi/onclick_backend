package com.onesley.oneclick.permission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AppPermissionRepository extends JpaRepository<AppPermission, UUID> {
}
