package com.onesley.oneclick.modules.financial.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.onesley.oneclick.modules.financial.internal.Contract;
import com.onesley.oneclick.modules.financial.internal.Invoice;
import com.onesley.oneclick.modules.financial.internal.InvoiceLine;
import com.onesley.oneclick.modules.financial.internal.WalletTransaction;

public final class FinancialDtos {

    private FinancialDtos() {}

    // ─── Contract ────────────────────────────────────────────────────────────

    public record ContractDto(UUID id, UUID restaurantId, String contractNumber, BigDecimal commissionRate,
                              LocalDate startsAt, LocalDate endsAt, String status, Instant createdAt) {
        public static ContractDto from(Contract c) {
            return new ContractDto(c.getId(), c.getRestaurantId(), c.getContractNumber(),
                c.getCommissionRate(), c.getStartsAt(), c.getEndsAt(), c.getStatus(), c.getCreatedAt());
        }
    }

    public record ContractCreateDto(
        @NotNull UUID restaurantId,
        @NotBlank String contractNumber,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal commissionRate,
        @NotNull LocalDate startsAt,
        LocalDate endsAt
    ) {}

    public record ContractUpdateDto(
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal commissionRate,
        LocalDate endsAt,
        @Pattern(regexp = "^(draft|active|paused|terminated)$") String status
    ) {}

    // ─── Invoice ─────────────────────────────────────────────────────────────

    public record InvoiceDto(UUID id, UUID restaurantId, String invoiceNumber, LocalDate periodStart,
                             LocalDate periodEnd, BigDecimal subtotal, BigDecimal tvaAmount, BigDecimal totalTtc,
                             String status, LocalDate issuedAt, LocalDate dueAt, Instant paidAt, Instant createdAt) {
        public static InvoiceDto from(Invoice i) {
            return new InvoiceDto(i.getId(), i.getRestaurantId(), i.getInvoiceNumber(),
                i.getPeriodStart(), i.getPeriodEnd(), i.getSubtotal(), i.getTvaAmount(), i.getTotalTtc(),
                i.getStatus(), i.getIssuedAt(), i.getDueAt(), i.getPaidAt(), i.getCreatedAt());
        }
    }

    public record InvoiceCreateDto(
        @NotNull UUID restaurantId,
        @NotBlank String invoiceNumber,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd
    ) {}

    public record InvoiceUpdateDto(
        BigDecimal subtotal,
        BigDecimal tvaAmount,
        BigDecimal totalTtc,
        @Pattern(regexp = "^(draft|sent|paid|overdue|cancelled)$") String status,
        LocalDate issuedAt,
        LocalDate dueAt
    ) {}

    // ─── InvoiceLine ─────────────────────────────────────────────────────────

    public record InvoiceLineDto(UUID id, UUID invoiceId, String label, BigDecimal quantity,
                                 BigDecimal unitPrice, BigDecimal lineTotal, Integer sortOrder) {
        public static InvoiceLineDto from(InvoiceLine l) {
            return new InvoiceLineDto(l.getId(), l.getInvoiceId(), l.getLabel(), l.getQuantity(),
                l.getUnitPrice(), l.getLineTotal(), l.getSortOrder());
        }
    }

    public record InvoiceLineCreateDto(
        @NotNull UUID invoiceId,
        @NotBlank String label,
        @NotNull @DecimalMin("0.00") BigDecimal quantity,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
        Integer sortOrder
    ) {}

    // ─── WalletTransaction ───────────────────────────────────────────────────

    public record WalletTxDto(UUID id, UUID restaurantId, String type, BigDecimal amount,
                              BigDecimal balanceAfter, String reason, UUID referenceId, String referenceType,
                              Instant createdAt, UUID createdById) {
        public static WalletTxDto from(WalletTransaction t) {
            return new WalletTxDto(t.getId(), t.getRestaurantId(), t.getType(), t.getAmount(),
                t.getBalanceAfter(), t.getReason(), t.getReferenceId(), t.getReferenceType(),
                t.getCreatedAt(), t.getCreatedById());
        }
    }

    public record WalletTxCreateDto(
        @NotNull UUID restaurantId,
        @NotNull @Pattern(regexp = "^(credit|debit|commission|payout|adjustment)$") String type,
        @NotNull BigDecimal amount,
        String reason,
        UUID referenceId,
        String referenceType
    ) {}
}
