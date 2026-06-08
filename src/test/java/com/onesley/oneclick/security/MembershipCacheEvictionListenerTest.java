package com.onesley.oneclick.security;

import com.onesley.oneclick.shared.events.MembershipActivatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit isolé (Mockito) de {@link MembershipCacheEvictionListener} — P2.
 * Vérifie que l'éviction du cache userDetails est déclenchée sur activation de membership.
 */
@ExtendWith(MockitoExtension.class)
class MembershipCacheEvictionListenerTest {

    @Mock OneClickUserDetailsService userDetailsService;
    @InjectMocks MembershipCacheEvictionListener listener;

    @Test
    void onMembershipActivated_evictsTargetUser() {
        UUID userId = UUID.randomUUID();
        listener.onMembershipActivated(new MembershipActivatedEvent(userId, UUID.randomUUID(), Instant.now()));
        verify(userDetailsService).evictUser(userId);
    }

    @Test
    void nullEventOrUser_noEviction() {
        listener.onMembershipActivated(new MembershipActivatedEvent(null, UUID.randomUUID(), Instant.now()));
        verifyNoInteractions(userDetailsService);
    }
}
