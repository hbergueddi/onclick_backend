package com.onesley.oneclick.dto.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code invoice_lines} (généré par scripts/scaffold-jpa.mjs).
 */
public record InvoiceLineDto(
    UUID id,
    UUID invoiceId,
    String label,
    String description,
    BigDecimal quantity,
    BigDecimal unitPriceHt,
    BigDecimal totalHt,
    Integer sortOrder,
    Instant createdAt,
    Instant updatedAt
) {
}
