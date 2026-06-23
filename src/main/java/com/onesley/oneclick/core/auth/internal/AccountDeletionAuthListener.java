package com.onesley.oneclick.core.auth.internal;

import com.onesley.oneclick.shared.events.AccountDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Révoque tous les refresh tokens d'un utilisateur lorsqu'il supprime son compte.
 *
 * <p>Réagit à {@link AccountDeletedEvent} publié par {@code core.identity} — frontière
 * Modulith respectée (identity ne dépend pas de auth). {@code @ApplicationModuleListener}
 * = async + {@code REQUIRES_NEW} + after-commit : la révocation s'applique une fois la
 * suppression committée. Sans ça, un refresh token volé/persisté resterait utilisable.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class AccountDeletionAuthListener {

    private final AuthService authService;

    @ApplicationModuleListener
    public void onAccountDeleted(AccountDeletedEvent event) {
        int revoked = authService.revokeAllForUser(event.userId());
        log.info("[account-deletion] {} refresh token(s) révoqué(s) pour le compte supprimé {}",
            revoked, event.userId());
    }
}
