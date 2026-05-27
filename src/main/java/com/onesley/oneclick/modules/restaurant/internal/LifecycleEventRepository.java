package com.onesley.oneclick.modules.restaurant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository {@link LifecycleEvent} — journal append-only du cycle de vie resto.
 */
@Repository
public interface LifecycleEventRepository extends JpaRepository<LifecycleEvent, UUID> {

    List<LifecycleEvent> findAllByOrderByCreatedAtDesc();
}
