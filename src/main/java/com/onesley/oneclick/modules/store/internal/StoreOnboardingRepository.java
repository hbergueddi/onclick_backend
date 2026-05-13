package com.onesley.oneclick.modules.store.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface StoreOnboardingRepository extends JpaRepository<StoreOnboardingRequest, UUID> {

    @Query("SELECT r FROM StoreOnboardingRequest r WHERE r.deletedAt IS NULL ORDER BY r.createdAt DESC")
    List<StoreOnboardingRequest> findAllActive();

    @Query("SELECT r FROM StoreOnboardingRequest r WHERE r.status = :status AND r.deletedAt IS NULL ORDER BY r.createdAt DESC")
    List<StoreOnboardingRequest> findByStatus(String status);
}
