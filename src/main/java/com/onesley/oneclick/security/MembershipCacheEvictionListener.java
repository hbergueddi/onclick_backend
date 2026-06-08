package com.onesley.oneclick.security;

import com.onesley.oneclick.shared.events.MembershipActivatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Évince le cache {@code userDetails} d'un membre quand sa membership devient (ou redevient)
 * active (P2). Ses autorités programme — pliées depuis ses memberships actives (P1) — changent,
 * donc son {@code OneClickUserDetails} mis en cache (Redis, TTL 1h) est périmé.
 *
 * <p>Pourquoi un listener côté {@code security} et pas un appel direct depuis le module
 * {@code membership} : {@code security} dépend déjà de {@code membership.api} (le pliage). Un appel
 * {@code membership → security} créerait un cycle Modulith. On découple donc par event
 * ({@link MembershipActivatedEvent}, dans {@code shared} — module OPEN) consommé ICI.</p>
 *
 * <p>{@code AFTER_COMMIT} : on évince une fois la membership réellement persistée (sinon un échec
 * de commit laisserait un cache évincé incohérent). L'éviction elle-même est un simple
 * {@code @CacheEvict} (pas de DB) porté par {@link OneClickUserDetailsService#evictUser}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
class MembershipCacheEvictionListener {

    private final OneClickUserDetailsService userDetailsService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembershipActivated(MembershipActivatedEvent ev) {
        if (ev == null || ev.userId() == null) {
            return;
        }
        userDetailsService.evictUser(ev.userId());
        log.debug("[membership/evict] userDetails évincé user={} tenant={}", ev.userId(), ev.tenantId());
    }
}
