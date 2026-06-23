package com.onesley.oneclick.core.media.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository {@link Media} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface MediaRepository extends JpaRepository<Media, UUID>, JpaSpecificationExecutor<Media> {

    /** Médias d'un type d'entité (ex: "restaurant") pour la modération admin. */
    List<Media> findByEntityTypeAndDeletedAtIsNullOrderByCreatedAtDesc(String entityType);

    Optional<Media> findByIdAndDeletedAtIsNull(UUID id);

    /** Médias actifs d'une entité (ex: photos d'un restaurant), triés sort_order puis created_at. */
    List<Media> findByEntityTypeAndEntityIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(
        String entityType, UUID entityId);

    /** Nombre de médias actifs d'une entité — application de quota (ex: max 5 photos restaurant). */
    long countByEntityTypeAndEntityIdAndDeletedAtIsNull(String entityType, UUID entityId);
}
