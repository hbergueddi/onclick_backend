package com.onesley.oneclick.modules.social.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface EliteApplicationRepository extends JpaRepository<EliteApplication, UUID> {

    @Query("SELECT a FROM EliteApplication a WHERE a.deletedAt IS NULL ORDER BY a.createdAt DESC")
    List<EliteApplication> findAllActive();

    @Query("SELECT a FROM EliteApplication a WHERE a.userId = :userId AND a.deletedAt IS NULL ORDER BY a.createdAt DESC")
    List<EliteApplication> findByUser(UUID userId);

    @Query("SELECT a FROM EliteApplication a WHERE a.status = :status AND a.deletedAt IS NULL ORDER BY a.createdAt DESC")
    List<EliteApplication> findByStatus(String status);
}
