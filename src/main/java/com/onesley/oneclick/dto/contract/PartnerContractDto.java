package com.onesley.oneclick.dto.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DTO pour {@code partner_contracts} (généré par scripts/scaffold-jpa.mjs).
 */
public record PartnerContractDto(
    UUID id,
    UUID restaurantId,
    BigDecimal commissionRate,
    LocalDate contractStart,
    LocalDate contractEnd,
    String paymentTerms,
    Boolean autoRenew,
    String status,
    Instant createdAt,
    Instant updatedAt,
    String representedBy,
    String representedTitle,
    String restaurantAddress,
    String restaurantCity,
    String restaurantPhone,
    BigDecimal clientCommissionRate,
    BigDecimal walletAdminRate,
    BigDecimal oneclickCommissionRate,
    UUID templateId,
    Map<String, Object> contractSnapshot,
    Instant signedAt,
    String signedBy,
    String contractNumber,
    Instant expirationNotifiedAt,
    String raisonSociale,
    String formeJuridique,
    String numeroRc,
    String numeroIf,
    String numeroIce,
    String capitalSocial,
    String banque,
    String rib,
    Integer capaciteCouverts,
    String horairesExploitation,
    String joursFermeture,
    Integer dureeEngagementMois,
    Integer preavisResiliationMois,
    BigDecimal penaliteResiliation,
    BigDecimal plafondCommissionMensuel,
    String lieuSignature,
    Integer nombreExemplaires,
    String restaurantName,
    UUID parentContractId,
    Integer renewalNumber,
    Instant expirationNotified30dAt,
    Instant expirationNotified15dAt,
    Instant expirationNotified7dAt,
    UUID createdBy,
    UUID modifiedBy
) {
}
