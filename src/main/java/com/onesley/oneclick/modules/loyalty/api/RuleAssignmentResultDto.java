package com.onesley.oneclick.modules.loyalty.api;

/**
 * Résultat d'une opération d'assignation en masse (assign / unassign / sync)
 * d'une tier-rule plateforme (Gap #1, FORGE).
 *
 * @param affected nombre de gain_rules créées/mises à jour (assign/sync) ou
 *                 supprimées (unassign).
 */
public record RuleAssignmentResultDto(int affected) {
}
