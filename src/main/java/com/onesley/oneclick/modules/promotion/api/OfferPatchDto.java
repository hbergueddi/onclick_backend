package com.onesley.oneclick.modules.promotion.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de mise à jour partielle d'une offre.
 *
 * <p>Sémantique PATCH : tous les champs sont optionnels (null = pas de
 * modification). Seuls les champs explicitement renseignés sont appliqués.
 *
 * <p>Notes métier :
 * <ul>
 *   <li>{@code type} ∈ {"promo", "bonus", "reco"} — validation Bean + DB CHECK</li>
 *   <li>{@code pts} > 0 quand renseigné — validation Bean + DB CHECK</li>
 *   <li>{@code startsAt} / {@code expiresAt} : si les deux sont renseignés, le
 *       service vérifie {@code expiresAt > startsAt} (BadRequest sinon).</li>
 * </ul>
 *
 * <p>Note : {@code imageUrl} / {@code status} mentionnés dans la spec ne sont
 * pas exposés ici car non présents dans le schéma DB actuel (la colonne
 * persistée correspondante est {@code enabled} — un toggle booléen).
 */
public record OfferPatchDto(
    String title,
    String description,
    Instant startsAt,
    Instant expiresAt,
    @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal discountPct,
    @DecimalMin("0.00") BigDecimal discountAmount,
    Boolean enabled,
    @Pattern(regexp = "^(promo|bonus|reco)$") String type,
    @Positive Integer pts
) {
}
