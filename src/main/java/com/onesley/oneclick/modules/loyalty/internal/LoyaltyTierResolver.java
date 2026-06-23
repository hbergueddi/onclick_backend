package com.onesley.oneclick.modules.loyalty.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Source <b>unique</b> de résolution du palier de fidélité (CL-2).
 *
 * <p>Avant : le nom du palier était calculé à <b>deux</b> endroits divergents — {@code WalletPassService}
 * (seuils Ruby/Sapphire/Émeraude/Black <b>hardcodés</b>) et {@code LoyaltyService} (CH-3, lecture table
 * {@code tiers} par tenant, {@code null} si non configuré). Un wallet pouvait afficher « Sapphire » sans
 * que le push « palier atteint » ne parte, voire l'inverse si un admin éditait la table {@code tiers}.
 *
 * <p>Désormais un seul algorithme, utilisé par le wallet pass <b>et</b> par CH-3 :
 * <ol>
 *   <li><b>DB d'abord</b> — table {@code tiers} du tenant (seuils {@code min_points} éditables, règle
 *       anti-hardcode {@code feedback_tier_source_unique}) : le palier au {@code min_points} le plus
 *       élevé qui reste ≤ {@code points} ;</li>
 *   <li><b>Fallback canonique</b> — si le tenant n'a configuré aucun palier (cas par défaut : la table
 *       {@code tiers} n'est seedée par aucune migration), on applique les seuils historiques OneClick
 *       (Ruby 0 / Sapphire 1 500 / Émeraude 5 000 / Black 10 000). Garantit un résultat <b>non-null</b>
 *       et un comportement identique au wallet pré-CL-2.</li>
 * </ol>
 *
 * <p>Conséquence : wallet pass + push « palier atteint » + {@code useClientTier} front lisent la même
 * vérité ; si un admin peuple la table {@code tiers}, les deux suivent automatiquement.
 */
@Component
@RequiredArgsConstructor
public class LoyaltyTierResolver {

    private final TierRepository tierRepository;

    /**
     * Nom du palier atteint pour {@code points} au sein de {@code tenantId}. DB d'abord, sinon
     * fallback canonique. <b>Jamais {@code null}</b> (le palier le plus bas du fallback est « Ruby » à 0).
     */
    public String tierNameFor(int points, UUID tenantId) {
        if (tenantId != null) {
            String dbTier = tierRepository.findAllByTenantId(tenantId).stream()
                .filter(t -> t.getMinPoints() != null && points >= t.getMinPoints())
                .max(Comparator.comparingInt(Tier::getMinPoints))
                .map(Tier::getName)
                .orElse(null);
            if (dbTier != null) return dbTier;
        }
        return canonicalTierName(points);
    }

    /**
     * Seuil ({@code min_points}) du <b>prochain</b> palier au-dessus de {@code points} ({@code -1} si déjà
     * au sommet). DB d'abord (paliers du tenant), sinon fallback canonique.
     */
    public int nextTierPoints(int points, UUID tenantId) {
        if (tenantId != null) {
            List<Tier> tiers = tierRepository.findAllByTenantId(tenantId);
            if (!tiers.isEmpty()) {
                return tiers.stream()
                    .map(Tier::getMinPoints)
                    .filter(Objects::nonNull)
                    .filter(m -> m > points)
                    .min(Integer::compareTo)
                    .orElse(-1);
            }
        }
        return canonicalNextTier(points);
    }

    /**
     * Paliers charte (fallback quand le tenant n'a aucun palier en base) — noms sommeliers + seuils
     * maquette validés client (Connaisseur 200 / Grand Cru 500 / Signature 1 000 / Ambassadeur 10 000 /
     * Table Secrète 50 000). Connaisseur = palier plancher (renvoyé même sous 200 pts). Aligné sur la
     * migration V98 (rebrand de la table {@code tiers}).
     */
    private String canonicalTierName(int points) {
        if (points >= 50_000) return "Table Secrète";
        if (points >= 10_000) return "Ambassadeur";
        if (points >= 1_000) return "Signature";
        if (points >= 500) return "Grand Cru";
        return "Connaisseur";
    }

    private int canonicalNextTier(int points) {
        if (points < 500) return 500;
        if (points < 1_000) return 1_000;
        if (points < 10_000) return 10_000;
        if (points < 50_000) return 50_000;
        return -1; // déjà au sommet (Table Secrète)
    }
}
