package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.RuleAssignmentDto;
import com.onesley.oneclick.modules.loyalty.api.RuleAssignmentResultDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * Service d'assignation en masse d'une tier-rule plateforme (FORGE) à N restaurants
 * — Gap #1 du portage legacy→Spring.
 *
 * <p>Modèle : Spring conserve UNE {@link GainRule} par restaurant (lue par snap2earn
 * en priorité). « Assigner » la tier-rule {@code R} au restaurant {@code X} = recopier
 * les champs de conversion de {@code R} dans {@code gain_rules.X} + tracer la source
 * ({@code source_tier_rule_id}). La logique de scan reste inchangée — exactement
 * l'intention du legacy ({@code restaurant_gain_rules.source_rule_id}).
 *
 * <p>Mapping des champs tier-rule → gain_rule :
 * <ul>
 * <li>{@code conversionRate} → {@code conversionRate}</li>
 * <li>{@code minTicket} → {@code minAmount}</li>
 * <li>{@code maxPointsPerTicket} → {@code capPerVisit} (plafond points/visite)</li>
 * <li>{@code enabled} → {@code isActive}</li>
 * </ul>
 *
 * <p>Gating RBAC ({@code LOYALTY_TIER}) porté par {@code LoyaltyTierRuleController}.
 * Ressource plateforme (admin-only) → pas d'ABAC owner.
 */
@Service
@RequiredArgsConstructor
public class RuleAssignmentService {

    private final GainRuleRepository gainRuleRepository;
    private final LoyaltyTierRuleRepository tierRuleRepository;

    /** Restaurants où la tier-rule est actuellement assignée (set d'IDs côté front). */
    @Transactional(readOnly = true)
    public List<RuleAssignmentDto> getAssignments(UUID tierRuleId) {
        return gainRuleRepository.findBySourceTierRuleIdAndDeletedAtIsNull(tierRuleId)
            .stream()
            .map(g -> new RuleAssignmentDto(g.getRestaurantId(), g.isActive(), g.getCreatedAt()))
            .toList();
    }

    /** Compteurs d'assignation par tier-rule source (badges « X restos » de la FORGE). */
    @Transactional(readOnly = true)
    public Map<UUID, Long> assignmentCounts() {
        return gainRuleRepository.countAssignmentsBySourceTierRule().stream()
            .collect(Collectors.toMap(
                row -> (UUID) row[0],
                row -> (Long) row[1]));
    }

    /**
     * Assigne la tier-rule à chaque restaurant : upsert de la gain_rule (création si
     * absente, sinon mise à jour en place) avec recopie des champs + lien source.
     * Idempotent.
     */
    @Transactional
    public RuleAssignmentResultDto assign(UUID tierRuleId, List<UUID> restaurantIds) {
        LoyaltyTierRule rule = requireTierRule(tierRuleId);
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return new RuleAssignmentResultDto(0);
        }
        int affected = 0;
        for (UUID restaurantId : restaurantIds) {
            GainRule gainRule = gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(restaurantId)
                .orElseGet(() -> new GainRule(UUID.randomUUID(), restaurantId, rule.getConversionRate()));
            copyFields(rule, gainRule);
            gainRuleRepository.save(gainRule);
            affected++;
        }
        return new RuleAssignmentResultDto(affected);
    }

    /**
     * Retire la tier-rule des restaurants ciblés : suppression (hard delete) des
     * gain_rules liées à cette source. Le restaurant retombe alors sur le taux par
     * défaut de snap2earn (fidèle au legacy qui supprime restaurant_gain_rules).
     */
    @Transactional
    public RuleAssignmentResultDto unassign(UUID tierRuleId, List<UUID> restaurantIds) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return new RuleAssignmentResultDto(0);
        }
        List<GainRule> linked = gainRuleRepository
            .findBySourceTierRuleIdAndRestaurantIdInAndDeletedAtIsNull(tierRuleId, restaurantIds);
        gainRuleRepository.deleteAll(linked);
        return new RuleAssignmentResultDto(linked.size());
    }

    /**
     * Re-synchronise toutes les gain_rules assignées depuis cette tier-rule (propage
     * une modification ultérieure de la règle source). La source fait foi.
     */
    @Transactional
    public RuleAssignmentResultDto sync(UUID tierRuleId) {
        LoyaltyTierRule rule = requireTierRule(tierRuleId);
        List<GainRule> linked = gainRuleRepository.findBySourceTierRuleIdAndDeletedAtIsNull(tierRuleId);
        for (GainRule gainRule : linked) {
            copyFields(rule, gainRule);
            gainRuleRepository.save(gainRule);
        }
        return new RuleAssignmentResultDto(linked.size());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private LoyaltyTierRule requireTierRule(UUID tierRuleId) {
        return tierRuleRepository.findByIdAndDeletedAtIsNull(tierRuleId)
            .orElseThrow(() -> new NotFoundException("LoyaltyTierRule", tierRuleId));
    }

    private void copyFields(LoyaltyTierRule source, GainRule target) {
        target.setConversionRate(source.getConversionRate());
        target.setMinAmount(source.getMinTicket());
        target.setCapPerVisit(source.getMaxPointsPerTicket());
        target.setActive(source.isEnabled());
        target.setSourceTierRuleId(source.getId());
    }
}
