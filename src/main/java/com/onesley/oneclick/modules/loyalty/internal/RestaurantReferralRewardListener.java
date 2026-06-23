package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.modules.loyalty.api.LoyaltyEarnDto;
import com.onesley.oneclick.shared.events.RestaurantReferralActivatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listener loyalty sur l'activation d'un parrainage RESTAURANT-à-RESTAURANT — crédite la récompense
 * de fidélité au <b>PARRAIN seul</b> (spec métier validée : le filleul ne reçoit rien).
 *
 * <p>C'est le module loyalty (propriétaire de {@code loyalty_transactions}/{@code loyalty_accounts})
 * qui réagit à l'event {@link RestaurantReferralActivatedEvent} publié par le module
 * {@code restaurant_referral}, sans dépendance directe {@code restaurant_referral → loyalty}.
 * Frontière Modulith respectée : la seule communication entrante est via
 * {@link com.onesley.oneclick.shared.events} (package OPEN).
 *
 * <h3>Où atterrissent les points du owner parrain ?</h3>
 * <p>Sur le compte fidélité {@code (clientId = referrerUserId, restaurantId = referrerRestaurantId)}
 * — c'est le mécanisme universel de crédit OneClick : {@link LoyaltyService#earnPoints(LoyaltyEarnDto)}
 * fait {@code findOrCreate(account)} + INSERT transaction {@code type='earn'} + UPDATE balance +
 * publie {@link com.onesley.oneclick.shared.events.LoyaltyEarnedEvent}. Un owner n'a pas de « compte
 * client » distinct : le modèle {@code loyalty_accounts} est générique {@code (user × restaurant)},
 * on l'instancie donc pour le couple (parrain, son resto) avec la raison dédiée
 * {@code RESTAURANT_REFERRAL} (audit + traçabilité de la source du gain).
 * Le tenant {@code oneclick} étant public, le gate d'adhésion de {@code earnPoints} est un no-op.
 *
 * <p>Async + after-commit : {@code @ApplicationModuleListener} = {@code @Async} +
 * {@code @Transactional(REQUIRES_NEW)} + {@code @TransactionalEventListener(AFTER_COMMIT)}. Le crédit
 * est donc appliqué une fois l'activation du parrainage committée — comme {@code ResourceBookingPunchListener}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RestaurantReferralRewardListener {

    /** Raison de transaction loyalty pour un gain issu d'un parrainage resto→resto. */
    static final String REASON = "RESTAURANT_REFERRAL";

    private final LoyaltyService loyaltyService;

    @ApplicationModuleListener
    public void onActivated(RestaurantReferralActivatedEvent event) {
        if (event.rewardPoints() <= 0) {
            return; // rien à créditer
        }
        if (event.referrerUserId() == null || event.referrerRestaurantId() == null) {
            log.warn("[restaurant-referral] event activé sans parrain/resto (referral={}) — skip",
                event.referralId());
            return;
        }
        try {
            loyaltyService.earnPoints(new LoyaltyEarnDto(
                event.referrerUserId(),
                event.referrerRestaurantId(),
                event.rewardPoints(),
                null, // pas de montant — gain de parrainage, pas un ticket
                REASON
            ));
        } catch (Exception ex) {
            log.warn("[restaurant-referral] échec crédit points parrain (referral={}, user={}, resto={}): {}",
                event.referralId(), event.referrerUserId(), event.referrerRestaurantId(), ex.getMessage());
        }
    }
}
