package com.onesley.oneclick.modules.loyalty.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AIUsageRepository extends JpaRepository<AIUsage, UUID> {
    Optional<AIUsage> findByUserId(UUID userId);
}
