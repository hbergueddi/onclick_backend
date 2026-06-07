package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.exception.UnauthorizedException;
import com.onesley.oneclick.security.SecurityHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import com.onesley.oneclick.core.notification.api.NotificationDtos.StaffNotificationPrefsDto;
import lombok.RequiredArgsConstructor;

/**
 * Service des préférences de notifications staff (Gap #5 — port legacy B.5).
 *
 * <p>Self-service strict : toutes les opérations sont scopées sur le user courant
 * ({@link SecurityHelper#currentUserId()}). Aucun paramètre {@code userId} exposé —
 * impossible de lire/écrire les préférences d'un autre user, même pour un admin
 * (ce sont des préférences personnelles, pas des données administrables).
 *
 * <p>Pattern microservice (cohérent avec {@link NotificationService}) : pas de
 * référence aux entities {@code User}/{@code Tenant} ; FK matérialisée en UUID.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StaffNotificationPreferenceService {

    private final StaffNotificationPreferenceRepository repository;

    /**
     * Préférences du user courant. Renvoie les défauts (tous {@code true}) si aucune
     * row n'existe encore — la persistance se fait au 1er {@link #updateMine}.
     */
    public StaffNotificationPrefsDto getMine() {
        UUID userId = requireCurrentUser();
        return repository.findByUserId(userId)
            .map(StaffNotificationPreference::toDto)
            .orElseGet(StaffNotificationPrefsDto::allEnabled);
    }

    /** Upsert des 5 toggles pour le user courant (crée la row si absente). */
    @Transactional
    public StaffNotificationPrefsDto updateMine(StaffNotificationPrefsDto dto) {
        UUID userId = requireCurrentUser();
        StaffNotificationPreference pref = repository.findByUserId(userId)
            .orElseGet(() -> new StaffNotificationPreference(UUID.randomUUID(), userId));
        pref.apply(dto);
        return repository.save(pref).toDto();
    }

    /** Garde défensive : ne devrait jamais déclencher derrière {@code @PreAuthorize}. */
    private UUID requireCurrentUser() {
        UUID userId = SecurityHelper.currentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Authentification requise");
        }
        return userId;
    }
}
