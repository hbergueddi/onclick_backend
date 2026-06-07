package com.onesley.oneclick.core.email.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository {@link EmailBounce} — suppression list emails (Gap #4). */
@Repository
public interface EmailBounceRepository extends JpaRepository<EmailBounce, UUID> {

    /** Lookup par adresse normalisée (lowercase) — clé d'upsert + check suppression. */
    Optional<EmailBounce> findByEmail(String email);

    /** True si l'adresse est suppressed (bounce permanent / complaint). */
    boolean existsByEmailAndSuppressedTrue(String email);

    /** Liste admin (monitoring) — plus récents d'abord. */
    List<EmailBounce> findAllByOrderByLastBouncedAtDesc();
}
