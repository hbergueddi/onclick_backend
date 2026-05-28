package com.onesley.oneclick.modules.loyalty.api;

import java.math.BigDecimal;

/**
 * Agrégat plateforme des tickets scannés (Snap2Earn) — carte « Tickets & Lounge »
 * du PulseBoard admin (page Tableaux Pulse).
 *
 * <ul>
 *   <li>{@code ticketCount}   — nombre de tickets scannés (transactions earn issues d'un scan)</li>
 *   <li>{@code pointsEmitted} — total des points crédités par ces scans</li>
 *   <li>{@code totalAmount}   — CA cumulé scanné (somme des montants tickets, MAD)</li>
 * </ul>
 *
 * <p>Pas de champ « validé » : côté Spring un ticket scanné est crédité
 * immédiatement (le workflow de modération du legacy Supabase a été abandonné),
 * donc {@code ticketCount} = tickets validés.
 */
public record ScannedTicketStatsDto(
    long ticketCount,
    long pointsEmitted,
    BigDecimal totalAmount
) {
}
