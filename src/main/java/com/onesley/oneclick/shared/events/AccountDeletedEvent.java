package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand un utilisateur supprime <b>son propre</b> compte (self-service,
 * {@code DELETE /api/users/me}). Émis par {@code core.identity} après le soft-delete +
 * anonymisation PII.
 *
 * <p>Consommé hors du module identity (frontière Modulith — pas d'appel direct
 * inter-module) :
 * <ul>
 *   <li>{@code core.auth} → révoque tous les refresh tokens du user (les sessions
 *       serveur ne survivent pas à la suppression) ;</li>
 *   <li>{@code core.notification} → purge les device tokens (plus aucun push possible).</li>
 * </ul>
 */
public record AccountDeletedEvent(UUID userId, Instant occurredAt) {}
