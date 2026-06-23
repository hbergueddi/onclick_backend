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

    @Override
    public List<UUID> adminUserIds() {
        return userRepository.findIdsByRoleCode("SUPERADMIN");
    }

    @Override
    public Optional<UUID> tenantIdById(UUID userId) {
        if (userId == null) return Optional.empty();
        return userRepository.findById(userId)
            .filter(u -> u.getDeletedAt() == null)
            .map(com.onesley.oneclick.core.identity.api.User::getTenantId);
    }

    @Override
    public Optional<UserName> findByIdentifier(String identifier, UUID tenantId) {
        if (tenantId == null || identifier == null) return Optional.empty();
        String clean = identifier.trim();
        if (clean.isEmpty()) return Optional.empty();

        // Auto-detect format (aligné sur le legacy RPC add_pcc_family_member) :
        //   email (contient '@') | code parrainage (préfixe OC-) | sinon téléphone.
        final Optional<com.onesley.oneclick.core.identity.api.User> match;
        if (clean.contains("@")) {
            match = userRepository.findByEmailIgnoreCaseAndTenant(clean, tenantId);
        } else if (clean.toUpperCase().startsWith("OC-")) {
            match = userRepository.findByReferralCodeIgnoreCaseAndTenant(clean, tenantId);
        } else {
            match = userRepository.findByNormalizedPhoneAndTenant(clean, tenantId);
        }
        // Le filtre deleted_at IS NULL est porté par chaque requête repo.
        return match.map(this::toName);
    }

    @Override
    public Optional<UserName> findByIdentifier(String identifier) {
        if (identifier == null) return Optional.empty();
        String clean = identifier.trim();
        if (clean.isEmpty()) return Optional.empty();

        // Même auto-détection que la variante scopée, mais sur les finders GLOBAUX (sans tenant).
        final Optional<com.onesley.oneclick.core.identity.api.User> match;
        if (clean.contains("@")) {
            match = userRepository.findByEmailIgnoreCase(clean);
        } else if (clean.toUpperCase().startsWith("OC-")) {
            match = userRepository.findByReferralCodeIgnoreCase(clean);
        } else {
            match = userRepository.findByPhone(clean);
        }
        return match.filter(u -> u.getDeletedAt() == null).map(this::toName);
    }

    private UserName toName(com.onesley.oneclick.core.identity.api.User u) {
        return new UserName(u.getId(), u.getFirstName(), u.getLastName(), u.getPhone(), u.getEmail(), u.getAvatarUrl());
    }
}
