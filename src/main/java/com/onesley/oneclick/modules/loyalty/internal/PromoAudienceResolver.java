package com.onesley.oneclick.modules.loyalty.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Résolveur d'audience d'un segment de push promo (Sprint R2 — parité {@code send-promo-push}).
 *
 * <p>Mapping legacy → schéma Spring (le legacy ciblait {@code loyalty_points}/{@code scanned_tickets}
 * à plat ; ici on passe par {@code loyalty_accounts} (1 ligne par client×resto, avec {@code tier_id})
 * joint à {@code loyalty_transactions} (mouvements typés) — le « ticket » legacy = transaction
 * {@code reason LIKE 'snap2earn%'}) :
 * <ul>
 *   <li><b>all / tous</b> : tous les clients ayant un compte fidélité sur ce resto.</li>
 *   <li><b>fideles</b> : ≥ 3 scans snap2earn sur 90 jours.</li>
 *   <li><b>nouveaux</b> : 1ʳᵉ transaction sur ce resto datée de moins de 30 jours.</li>
 *   <li><b>inactifs</b> : compte actif mais 0 scan snap2earn sur 60 jours.</li>
 *   <li><b>ruby / sapphire / emeraude / black</b> : palier courant du compte ({@code tiers.name}).</li>
 * </ul>
 *
 * <p>Lecture read-model en SQL natif délibéré (cf package-info loyalty — découplage de schéma).
 */
@Component
@Slf4j
public class PromoAudienceResolver {

    /**
     * Slug du tenant <b>public</b> (catalogue OneClick standard) : seul tenant SANS gate d'adhésion.
     * Constante canonique du projet (cf. {@code MembershipDirectoryApi.publicTenantId} / {@code TenantScope}).
     */
    private static final String PUBLIC_TENANT_SLUG = "oneclick";

    @PersistenceContext
    private EntityManager em;

    /** Résout les {@code client_id} (= user ids) cibles d'un segment pour un restaurant. */
    public List<UUID> resolve(UUID restaurantId, String segment) {
        if (restaurantId == null) return List.of();
        String s = segment == null ? "all" : segment.trim().toLowerCase();
        List<UUID> candidates = switch (s) {
            case "all", "tous" -> byRestaurant("""
                SELECT la.client_id FROM loyalty_accounts la
                WHERE la.restaurant_id = :rid AND la.deleted_at IS NULL""", restaurantId);
            case "fideles", "fidèles" -> byRestaurant("""
                SELECT la.client_id FROM loyalty_accounts la
                JOIN loyalty_transactions lt ON lt.account_id = la.id
                WHERE la.restaurant_id = :rid AND la.deleted_at IS NULL
                  AND lt.reason LIKE 'snap2earn%' AND lt.created_at > NOW() - INTERVAL '90 days'
                GROUP BY la.client_id HAVING count(*) >= 3""", restaurantId);
            case "nouveaux" -> byRestaurant("""
                SELECT la.client_id FROM loyalty_accounts la
                JOIN loyalty_transactions lt ON lt.account_id = la.id
                WHERE la.restaurant_id = :rid AND la.deleted_at IS NULL
                GROUP BY la.client_id HAVING min(lt.created_at) > NOW() - INTERVAL '30 days'""", restaurantId);
            case "inactifs" -> byRestaurant("""
                SELECT la.client_id FROM loyalty_accounts la
                JOIN loyalty_transactions lt ON lt.account_id = la.id
                WHERE la.restaurant_id = :rid AND la.deleted_at IS NULL
                GROUP BY la.client_id
                HAVING count(*) FILTER (
                    WHERE lt.reason LIKE 'snap2earn%' AND lt.created_at > NOW() - INTERVAL '60 days') = 0""",
                restaurantId);
            // Paliers charte (V98). Les anciennes clés (ruby/sapphire/emeraude/black) restent acceptées
            // et pointent vers le palier renommé correspondant → les promos déjà ciblées ne cassent pas.
            case "connaisseur", "ruby" -> byTier(restaurantId, "Connaisseur");
            case "grand cru", "grand_cru", "grandcru", "sapphire", "saphir" -> byTier(restaurantId, "Grand Cru");
            case "signature", "emeraude", "émeraude" -> byTier(restaurantId, "Signature");
            case "ambassadeur" -> byTier(restaurantId, "Ambassadeur");
            case "table secrète", "table_secrete", "tablesecrete", "black", "noir" -> byTier(restaurantId, "Table Secrète");
            default -> {
                log.warn("[promo-audience] segment inconnu '{}' → 0 destinataire", segment);
                yield List.of();
            }
        };
        // Garde-fou de périmètre tenant : une promo d'un resto appartenant à un tenant À ADHÉSION
        // (PCC/HOMU/…) ne doit cibler QUE les membres actifs de ce tenant — un client avec un
        // simple compte loyalty sur le resto mais SANS adhésion ne reçoit rien. Le tenant public
        // (oneclick) n'a pas de gate. Corrige la fuite « non-membre PCC reçoit une promo PCC ».
        return gateByTenantMembership(restaurantId, candidates);
    }

    /**
     * Restreint l'audience aux membres actifs du tenant du restaurant, SAUF si c'est le tenant
     * public ({@code oneclick}) où aucun filtre ne s'applique. Lecture read-model SQL natif
     * (cohérent avec le reste du résolveur ; aucune dépendance module ajoutée).
     */
    private List<UUID> gateByTenantMembership(UUID restaurantId, List<UUID> candidates) {
        if (candidates.isEmpty()) return candidates;
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT r.tenant_id, t.slug
                FROM restaurants r JOIN tenants t ON t.id = r.tenant_id
                WHERE r.id = :rid""")
            .setParameter("rid", restaurantId)
            .getResultList();
        if (rows.isEmpty() || rows.get(0)[0] == null) return candidates; // resto/tenant introuvable → no-op défensif
        UUID tenantId = toUuid(rows.get(0)[0]);
        String slug = String.valueOf(rows.get(0)[1]);
        if (PUBLIC_TENANT_SLUG.equals(slug)) return candidates; // tenant public → pas de gate

        @SuppressWarnings("unchecked")
        List<Object> members = em.createNativeQuery("""
                SELECT tm.user_id FROM tenant_memberships tm
                WHERE tm.tenant_id = :tid AND tm.status = 'active' AND tm.deleted_at IS NULL""")
            .setParameter("tid", tenantId)
            .getResultList();
        Set<UUID> activeMembers = members.stream().map(PromoAudienceResolver::toUuid).collect(Collectors.toSet());
        return candidates.stream().filter(activeMembers::contains).toList();
    }

    private List<UUID> byRestaurant(String sql, UUID restaurantId) {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery(sql).setParameter("rid", restaurantId).getResultList();
        return rows.stream().map(PromoAudienceResolver::toUuid).distinct().toList();
    }

    private List<UUID> byTier(UUID restaurantId, String tierName) {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("""
                SELECT la.client_id FROM loyalty_accounts la
                JOIN tiers t ON t.id = la.tier_id
                WHERE la.restaurant_id = :rid AND la.deleted_at IS NULL AND t.deleted_at IS NULL
                  AND lower(t.name) = lower(:tier)""")
            .setParameter("rid", restaurantId)
            .setParameter("tier", tierName)
            .getResultList();
        return rows.stream().map(PromoAudienceResolver::toUuid).distinct().toList();
    }

    /** Colonne {@code uuid} d'une requête native : driver pg → {@link UUID}, fallback parse défensif. */
    private static UUID toUuid(Object o) {
        return o instanceof UUID u ? u : UUID.fromString(String.valueOf(o));
    }
}
