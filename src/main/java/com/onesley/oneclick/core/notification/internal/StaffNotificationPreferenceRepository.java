package com.onesley.oneclick.core.notification.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Repository {@link StaffNotificationPreference} — 1 row max par user (user_id UNIQUE). */
@Repository
public interface StaffNotificationPreferenceRepository
    extends JpaRepository<StaffNotificationPreference, UUID> {

    Optional<StaffNotificationPreference> findByUserId(UUID userId);
}
