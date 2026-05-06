package com.onesley.oneclick.entity.reservation;

/**
 * Enum {@code public.reservation_status} — workflow d'une réservation.
 *
 * <p>Les noms Java sont identiques aux labels DB (Unicode supporté par
 * le langage Java). Évite un AttributeConverter custom, et garantit
 * une sérialisation Jackson naturelle (ex: {@code "demandée"}).
 *
 * <p>Workflow simplifié :
 * <pre>
 *   demandée → confirmée → placée → terminée → honorée
 *           ↘ refusée
 *           ↘ contre_proposition → confirmée
 *           ↘ annulée
 *           ↘ no_show
 *           ↘ en_attente (waitlist, déprécié depuis Session 38)
 * </pre>
 */
@SuppressWarnings("NonAsciiCharacters") // Volontaire — match exact avec l'enum DB
public enum ReservationStatus {
    demandée,
    confirmée,
    placée,
    terminée,
    annulée,
    honorée,
    no_show,
    refusée,
    contre_proposition,
    en_attente
}
