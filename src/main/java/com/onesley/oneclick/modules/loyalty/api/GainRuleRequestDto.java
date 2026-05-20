package com.onesley.oneclick.modules.loyalty.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO public {@code gain_rule_requests} — Sprint G.2.3 (workflow approbation
 * admin pour les règles de gain proposées par les restaurateurs).
 *
 * @param createdRuleId  UUID de la {@link GainRuleDto} créée si {@code status='approved'}
 */
public record GainRuleRequestDto(
    UUID id,
    UUID restaurantId,
    String name,
    String description,
    String type,
    BigDecimal conversionRate,
    Integer capPerVisit,
    Integer capPerMonth,
    BigDecimal minAmount,
    String status,
    String rejectionReason,
    UUID reviewedById,
    Instant reviewedAt,
    UUID createdRuleId,
    Instant createdAt
) {

    /** Création d'une demande (restaurateur). */
    public record CreateDto(
        @NotNull UUID restaurantId,
        @NotBlank @Size(min = 1, max = 128) String name,
        @Size(min = 1, max = 1024) String description,
        @Pattern(regexp = "^(standard|premium|event|loyalty)$") @Size(min = 1, max = 64) String type,
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal conversionRate,
        Integer capPerVisit,
        Integer capPerMonth,
        @DecimalMin("0.00") BigDecimal minAmount
    ) {}

    /** Refus d'une demande (admin) — motif requis. */
    public record RejectDto(
        @NotBlank @Size(min = 1, max = 1024) String rejectionReason
    ) {}
}
