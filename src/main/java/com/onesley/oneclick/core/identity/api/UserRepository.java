package com.onesley.oneclick.core.identity.api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import com.onesley.oneclick.core.identity.api.User;

/**
 * Repository {@link User} — accès CRUD + finders métier.
 *
 * <p>Toutes les méthodes filtrent implicitement {@code deleted_at IS NULL}
 * via le pattern soft-delete des services. Pour la lecture brute (admin),
 * utiliser {@link JpaRepository#findById(Object)} qui retourne aussi les rows
 * supprimées (à filtrer manuellement).
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /** Lookup par email (login). Insensible à la casse. */
    Optional<User> findByEmailIgnoreCase(String email);

    /** Lookup par téléphone (login OTP, recherche client par phone). */
    Optional<User> findByPhone(String phone);

    /** Existence rapide par email (signup uniqueness check). */
    boolean existsByEmailIgnoreCase(String email);

    /** Existence rapide par téléphone. */
    boolean existsByPhone(String phone);
}
