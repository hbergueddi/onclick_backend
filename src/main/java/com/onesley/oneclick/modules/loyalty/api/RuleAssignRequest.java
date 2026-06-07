package com.onesley.oneclick.modules.loyalty.api;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Corps des opérations d'assignation en masse (assign / unassign) d'une
 * tier-rule plateforme à un ensemble de restaurants (Gap #1, FORGE).
 *
 * <p>Liste vide tolérée (idempotence : 0 opération), conformément au legacy.
 */
public record RuleAssignRequest(
    @NotNull(message = "restaurantIds requis") List<UUID> restaurantIds
) {
}
