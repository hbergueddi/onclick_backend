package com.onesley.oneclick.modules.oneclickhi.api;

import jakarta.validation.constraints.Size;

import com.onesley.oneclick.modules.oneclickhi.internal.OneClickHIInvoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class OneClickHIDtos {

    private OneClickHIDtos() {}

    public record OneClickHIInvoiceDto(
        UUID id,
        UUID tenantId,
        UUID restaurantId,
        String invoiceNumber,
        String periodMonth,
        BigDecimal totalAmount,
        BigDecimal vatAmount,
        String status,
        String pdfUrl,
        BigDecimal credit3pct,
        Instant validatedAt,
        UUID validatedBy,
        Instant sentAt,
        String pdfPath,
        Instant createdAt
    ) {
        public static OneClickHIInvoiceDto from(OneClickHIInvoice i) {
            return new OneClickHIInvoiceDto(
                i.getId(), i.getTenantId(), i.getRestaurantId(),
                i.getInvoiceNumber(), i.getPeriodMonth(),
                i.getTotalAmount(), i.getVatAmount(), i.getStatus(),
                i.getPdfUrl(),
                i.getCredit3pct(), i.getValidatedAt(), i.getValidatedBy(),
                i.getSentAt(), i.getPdfPath(),
                i.getCreatedAt()
            );
        }
    }

    public record OneClickHIInvoiceCreateDto(
        UUID tenantId,
        UUID restaurantId,
        @Size(min = 1, max = 64) String invoiceNumber,
        @Size(min = 1, max = 64) String periodMonth,
        BigDecimal totalAmount,
        BigDecimal vatAmount,
        @Size(min = 1, max = 512) String pdfUrl,
        BigDecimal credit3pct
    ) {}

    public record OneClickHIInvoicePatchDto(
        @Size(min = 1, max = 64) String status,
        BigDecimal totalAmount,
        BigDecimal vatAmount,
        @Size(min = 1, max = 512) String pdfUrl,
        BigDecimal credit3pct,
        Instant validatedAt,
        UUID validatedBy,
        Instant sentAt,
        @Size(min = 1, max = 512) String pdfPath
    ) {}

    public record OneClickHICockpitDto(
        Long totalInvoices,
        Long draftInvoices,
        Long sentInvoices,
        Long paidInvoices,
        Long overdueInvoices,
        BigDecimal totalRevenue,
        BigDecimal monthlyRecurring,
        Long activeRestaurants
    ) {}

    public record RestaurantHIDto(
        UUID restaurantId,
        Long invoicesCount,
        BigDecimal totalBilled,
        BigDecimal totalPaid,
        BigDecimal outstanding,
        Instant lastInvoiceAt
    ) {}

    public record RestaurantHIChartPointDto(
        String periodMonth,
        BigDecimal amount,
        Integer ticketsCount
    ) {}
}
