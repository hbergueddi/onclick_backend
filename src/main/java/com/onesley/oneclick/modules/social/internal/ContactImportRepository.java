package com.onesley.oneclick.modules.social.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository {@link ContactImport} — INSERT + comptage pour le quota d'import.
 *
 * <p>Pas de soft delete (journal append-only). Le comptage utilise une fenêtre
 * glissante {@code created_at > (now - 24h)} (≠ « aujourd'hui calendaire ») —
 * aligné sur l'index {@code idx_contact_imports_user_created} (V64).
 */
@Repository
public interface ContactImportRepository extends JpaRepository<ContactImport, UUID> {

    /** Nombre d'imports déclenchés par {@code userId} depuis l'instant {@code since} (inclus exclu). */
    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);
}
