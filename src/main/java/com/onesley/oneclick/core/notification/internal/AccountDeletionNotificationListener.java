package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.shared.events.AccountDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Purge les device tokens (push) d'un utilisateur lorsqu'il supprime son compte.
 *
 * <p>Réagit à {@link AccountDeletedEvent} publié par {@code core.identity} — frontière Modulith
 * respectée (identity ne dépend pas de notification). {@code @ApplicationModuleListener} = async +
 * {@code REQUIRES_NEW} + after-commit. Hard delete (l'entité {@code DeviceToken} n'a pas de
 * soft-delete) : après suppression, plus aucun push ne peut cibler l'ex-compte (RGPD + sécurité).
 */
@Component
@Slf4j
@RequiredArgsConstructor
class AccountDeletionNotificationListener {

    private final DeviceTokenRepository deviceTokenRepository;

    @ApplicationModuleListener
    public void onAccountDeleted(AccountDeletedEvent event) {
        long purged = deviceTokenRepository.deleteByUserId(event.userId());
        log.info("[account-deletion] {} device token(s) purgé(s) pour le compte supprimé {}",
            purged, event.userId());
    }
}
