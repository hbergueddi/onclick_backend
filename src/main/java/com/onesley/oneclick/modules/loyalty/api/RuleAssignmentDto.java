package com.onesley.oneclick.modules.loyalty.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Assignation d'une tier-rule plateforme à un restaurant (Gap #1, FORGE).
 *
 * <p>Lecture pure du domaine loyalty ({@code gain_rules} filtrées par
 * {@code source_tier_rule_id}) — le nom/ville du restaurant est résolu côté
 * front depuis sa propre liste de restaurants (pas de dépendance cross-module).
 */
public record RuleAssignmentDto(
    UUID restaurantId,
    boolean enabled,
    Instant assignedAt
) {
}
