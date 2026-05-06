package com.onesley.oneclick.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Marqueur commun pour tous les événements métier OneClick.
 *
 * <p>Contrat minimal :
 * <ul>
 *   <li>{@link #eventId()} — UUID unique, utile pour idempotence côté listeners
 *       (ex: ne pas re-envoyer une notif déjà envoyée pour le même event)</li>
 *   <li>{@link #occurredAt()} — timestamp de la création de l'event (UTC)</li>
 *   <li>{@link #aggregateType()} — nom de l'entité métier ("reservation",
 *       "loyalty_points", etc.) — utile pour audit logging et metrics</li>
 *   <li>{@link #aggregateId()} — UUID de l'entité ; permet aux listeners de
 *       recharger l'état si besoin</li>
 * </ul>
 *
 * <p>Les sous-classes sont des records Java 26 portant les données nécessaires
 * au listener — pas de référence à l'entité JPA elle-même (les events sont
 * détachés de la persistence pour rester sérialisables et testables).
 *
 * <p>Publication : via {@link org.springframework.context.ApplicationEventPublisher}
 * injecté dans les services métier. Spring 6 traite tout objet (pas besoin
 * d'hériter de {@code ApplicationEvent}). On garde quand même cette interface
 * comme contrat documentaire.
 */
public interface DomainEvent {
    UUID eventId();
    Instant occurredAt();
    String aggregateType();
    UUID aggregateId();
}
