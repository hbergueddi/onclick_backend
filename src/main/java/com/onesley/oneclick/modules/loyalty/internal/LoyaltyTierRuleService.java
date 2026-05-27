package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTierRuleCreateDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTierRuleDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTierRulePatchDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Service du domaine « paliers de fidélité plateforme » (loyalty_tier_rules).
 *
 * <p>CRUD admin-only — le gating RBAC {@code LOYALTY_TIER} est porté par
 * {@code LoyaltyTierRuleController}. Aucun scoping owner (ressource plateforme).
 * Suppression = soft delete ({@code deleted_at}).
 */
@Service
@RequiredArgsConstructor
public class LoyaltyTierRuleService {

    private final LoyaltyTierRuleRepository repository;

    @Transactional(readOnly = true)
    public List<LoyaltyTierRuleDto> list() {
        return repository.findAllByDeletedAtIsNullOrderByMinTicketAsc()
            .stream().map(LoyaltyTierRule::toDto).toList();
    }

    @Transactional
    public LoyaltyTierRuleDto create(LoyaltyTierRuleCreateDto dto) {
        LoyaltyTierRule rule = new LoyaltyTierRule(UUID.randomUUID(), dto.name().trim());
        applyCommonFields(rule,
            dto.description(), dto.type(), dto.conversionRate(), dto.minTicket(),
            dto.maxPointsPerTicket(), dto.periodType(), dto.periodValue(),
            dto.benefitDurationDays(), dto.minSpendMonthly(), dto.enabled());
        return repository.save(rule).toDto();
    }

    @Transactional
    public LoyaltyTierRuleDto patch(UUID id, LoyaltyTierRulePatchDto dto) {
        LoyaltyTierRule rule = repository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("LoyaltyTierRule", id));
        if (dto.name() != null && !dto.name().isBlank()) rule.setName(dto.name().trim());
        applyCommonFields(rule,
            dto.description(), dto.type(), dto.conversionRate(), dto.minTicket(),
            dto.maxPointsPerTicket(), dto.periodType(), dto.periodValue(),
            dto.benefitDurationDays(), dto.minSpendMonthly(), dto.enabled());
        return repository.save(rule).toDto();
    }

    @Transactional
    public void delete(UUID id) {
        LoyaltyTierRule rule = repository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("LoyaltyTierRule", id));
        rule.markDeleted();
        repository.save(rule);
    }

    /** Applique les champs non-null partagés create/patch (convention partial update). */
    private void applyCommonFields(
        LoyaltyTierRule rule,
        String description, String type, java.math.BigDecimal conversionRate,
        java.math.BigDecimal minTicket, Integer maxPointsPerTicket, String periodType,
        Integer periodValue, Integer benefitDurationDays, java.math.BigDecimal minSpendMonthly,
        Boolean enabled
    ) {
        if (description != null) rule.setDescription(description);
        if (type != null) rule.setType(type);
        if (conversionRate != null) rule.setConversionRate(conversionRate);
        if (minTicket != null) rule.setMinTicket(minTicket);
        if (maxPointsPerTicket != null) rule.setMaxPointsPerTicket(maxPointsPerTicket);
        if (periodType != null) rule.setPeriodType(periodType);
        if (periodValue != null) rule.setPeriodValue(periodValue);
        if (benefitDurationDays != null) rule.setBenefitDurationDays(benefitDurationDays);
        if (minSpendMonthly != null) rule.setMinSpendMonthly(minSpendMonthly);
        if (enabled != null) rule.setEnabled(enabled);
    }
}
