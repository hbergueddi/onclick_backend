package com.onesley.oneclick.dto.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO pour {@code oneclick_hi_invoices} (généré par scripts/scaffold-jpa.mjs).
 */
public record OneclickHiInvoiceDto(
    UUID id,
    UUID restaurantId,
    LocalDate periodMonth,
    BigDecimal totalCa,
    BigDecimal credit3pct,
    BigDecimal commission2pct,
    String status,
    Instant paidAt,
    Instant reminderSentAt,
    String notes,
    Instant createdAt,
    Instant updatedAt,
    String invoiceNumber,
    BigDecimal tvaRate,
    BigDecimal tvaAmount,
    BigDecimal totalTtc,
    LocalDate dueDate,
    String pdfPath,
    Instant validatedAt,
    UUID validatedBy,
    Instant sentAt
) {
}
