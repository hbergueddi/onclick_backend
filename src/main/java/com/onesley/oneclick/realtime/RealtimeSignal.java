package com.onesley.oneclick.realtime;

import java.util.UUID;

/**
 * Signal d'invalidation temps réel <b>PII-free</b> poussé sur les topics STOMP par-ressource
 * ({@code /topic/reservations/{restaurantId}}, {@code /topic/dashboard/{restaurantId}}).
 *
 * <p>Le payload ne transporte <b>aucune</b> donnée métier sensible : seulement l'identifiant de
 * scope ({@code scopeId}, ex. {@code restaurantId}) et une {@code reason} courte
 * (ex. {@code reservation.created}). L'app staff reçoit ce signal et <b>re-fetch via REST</b>
 * (gardé par l'autorisation ABAC par-restaurant) — exactement le pattern de
 * {@code ResourceBookingDashboardPublisher} : le topic STOMP est un simple déclencheur
 * d'invalidation, jamais un canal de données.
 *
 * <p>C'est cette propriété qui permet de NE PAS gardez ces topics par-restaurant au niveau STOMP
 * (cf {@code StompAuthChannelInterceptor}, qui ne restreint que {@code /topic/admin/**}) : même un
 * abonné illégitime n'obtiendrait qu'un « ping », et le re-fetch REST resterait scopé serveur.
 */
public record RealtimeSignal(UUID scopeId, String reason) {

    /** Fabrique courte (lisibilité côté publishers/tests). */
    public static RealtimeSignal of(UUID scopeId, String reason) {
        return new RealtimeSignal(scopeId, reason);
    }
}
