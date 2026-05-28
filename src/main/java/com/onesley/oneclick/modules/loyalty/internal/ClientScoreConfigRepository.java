package com.onesley.oneclick.modules.loyalty.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository de la config singleton de notation client (V52).
 * Une seule ligne attendue ; {@link #findFirstByOrderByCreatedAtAsc} retourne la
 * config courante (la plus ancienne si plusieurs ont été insérées par erreur).
 */
@Repository
public interface ClientScoreConfigRepository extends JpaRepository<ClientScoreConfig, UUID> {
    Optional<ClientScoreConfig> findFirstByOrderByCreatedAtAsc();
}
