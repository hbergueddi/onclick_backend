package com.onesley.oneclick.dto.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code company_settings} (généré par scripts/scaffold-jpa.mjs).
 */
public record CompanySettingDto(
    UUID id,
    String raisonSociale,
    String formeJuridique,
    String capitalSocial,
    String adresse,
    String ville,
    String pays,
    String telephone,
    String email,
    String siteWeb,
    String numeroIce,
    String numeroIf,
    String numeroRc,
    String numeroCnss,
    String rib,
    String banque,
    String logoUrl,
    BigDecimal tvaRate,
    Integer paymentDelayDays,
    String invoicePrefix,
    String invoiceFooterText,
    Instant createdAt,
    Instant updatedAt,
    String numeroTp
) {
}
