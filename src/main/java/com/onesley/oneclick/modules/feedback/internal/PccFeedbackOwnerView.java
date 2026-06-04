package com.onesley.oneclick.modules.feedback.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Projection Spring Data — un avis de l'inbox owner, enrichi du nom de son resto ciblé.
 *
 * <h3>Modulith — pourquoi une interface + nativeQuery ?</h3>
 * <p>L'inbox owner restreint les avis à ceux ciblant un resto dont le caller est owner actif (ou
 * généraux, target NULL). Cette restriction joint {@code restaurant_staffs} + {@code restaurants},
 * entités qui vivent dans {@code modules.restaurant.internal} — non importables depuis
 * {@code modules.feedback} (CLOSED). On reste donc au niveau SQL (noms de tables) via une
 * {@code nativeQuery} dans {@link PccFeedbackRepository#findVisibleForOwner} ; le binding est
 * assuré par cette projection (getters {@code get<ColumnAlias>}). Même pattern que
 * {@code PccFamilyPointsHistoryView} (P2.c).</p>
 */
public interface PccFeedbackOwnerView {
    UUID getId();
    UUID getMemberId();
    UUID getTenantId();
    String getSentiment();
    String getCategory();
    String getComment();
    UUID getTargetRestaurantId();
    String getTargetRestaurantName();
    String getReplyText();
    UUID getReplyBy();
    Instant getReplyAt();
    boolean getReplyReadByMember();
    Instant getCreatedAt();
}
