package com.onesley.oneclick.core.identity.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.identity.api.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * P2 — Implémentation de {@link UserDirectoryApi} (résolution de noms hors module).
 *
 * <p>S'appuie sur {@link UserRepository} (entité {@code User} du module identity) —
 * la projection {@link UserName} ne lit que {@code id/firstName/lastName/phone}
 * (jamais le {@code role} lazy), donc aucun chargement transitif hors transaction.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class UserDirectoryService implements UserDirectoryApi {

    private final UserRepository userRepository;

    @Override
    public Optional<UserName> nameById(UUID userId) {
        if (userId == null) return Optional.empty();
        return userRepository.findById(userId)
            .filter(u -> u.getDeletedAt() == null)
            .map(this::toName);
    }

    @Override
    public List<UserName> namesByIds(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        return userRepository.findAllByIds(userIds).stream()
            .map(this::toName)
            .toList();
    }

    private UserName toName(com.onesley.oneclick.core.identity.api.User u) {
        return new UserName(u.getId(), u.getFirstName(), u.getLastName(), u.getPhone(), u.getEmail());
    }
}
