package com.onesley.oneclick.modules.loyalty.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO public d'une rédemption (audit Forge — {@code /forge/redemptions}).
 *
 * <p>La table {@code redemptions} ne stocke que {@code account_id} (FK vers
 * {@code loyalty_accounts}). Les champs {@code clientId} / {@code restaurantId} sont
 * dérivés serveur-side via JOIN {@code loyalty_accounts} (lecture cross-table en SQL
 * natif — Modulith CLOSED : pas d'import de l'entité {@code Restaurant}), et les noms
 * (client + restaurant) résolus pour l'affichage : {@code clientName} via
 * {@code UserDirectoryApi} (core.identity), {@code restaurantName} via read-view native
 * {@code restaurants}. Anti-N+1 : un seul batch de résolution de noms par page.
 *
 * <h3>Pourquoi pas de {@code status} / flags de fraude</h3>
 * <p>Le legacy {@code RedemptionAudit.tsx} lisait une table {@code redemption_events}
 * (statut accepté/refusé + flags ratio/plafond) qui n'existe pas dans le schéma Spring.
 * L'entité {@code Redemption} portée est un <b>audit dédié</b> des rédemptions effectives
 * (toutes acceptées par construction) — on expose ses champs réels, dont
 * {@code otpValidated} (vrai si la rédemption a dû passer une validation OTP sur gros
 * montant), qui joue le rôle de signal d'audit.
 */
public record RedemptionDto(
    UUID id,
    UUID accountId,
    /** Client propriétaire du compte (dérivé via JOIN loyalty_accounts). */
    UUID clientId,
    /** "Prénom Nom" résolu via UserDirectoryApi (null si introuvable/supprimé). */
    String clientName,
    /** Restaurant du compte (dérivé via JOIN loyalty_accounts). */
    UUID restaurantId,
    /** Nom du restaurant résolu via read-view native (null si introuvable). */
    String restaurantName,
    Integer pointsUsed,
    BigDecimal discountAmount,
    boolean otpValidated,
    Instant createdAt
) {}
