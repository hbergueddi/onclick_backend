package com.onesley.oneclick.core.auth.internal;

import com.onesley.oneclick.shared.events.AccountDeletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

/**
 * Test unitaire isolé (Mockito) du listener qui révoque les refresh tokens
 * d'un user à la suppression de son compte ({@link AccountDeletedEvent}).
 */
@ExtendWith(MockitoExtension.class)
class AccountDeletionAuthListenerTest {

    @Mock AuthService authService;
    @InjectMocks AccountDeletionAuthListener listener;

    @Test
    void onAccountDeleted_revokesAllRefreshTokensForUser() {
        UUID userId = UUID.randomUUID();
        listener.onAccountDeleted(new AccountDeletedEvent(userId, Instant.now()));
        verify(authService).revokeAllForUser(userId);
    }
}
