package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié par {@code PromoNotificationService.review()} quand une demande de push promo est
 * modérée ({@code approved} / {@code rejected}) — Lot B12.
 *
 * <p>Consommé par {@code NotificationEventHandler.onPromoRequestReviewed} → notif in-app au
 * <b>demandeur</b> ({@code requesterId} = {@code requested_by}) du verdict. Distinct du
 * {@link PromoApprovedEvent} (qui, lui, déclenche le fan-out FCM de l'audience du segment) :
 * ici on notifie uniquement le gérant qui a soumis la demande. In-app seul.
 *
 * <p>{@code requesterId} peut être {@code null} (demande sans demandeur tracé) → le handler skip.
 */
public record PromoRequestReviewedEvent(
    UUID requestId,
    UUID requesterId,
    boolean approved,
    String title,
    String rejectionReason,
    Instant occurredAt
) {
}
