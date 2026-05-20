package com.onesley.oneclick.modules.financial.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTOs publics du module financial.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Entity.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 */
public final class FinancialDtos {

    private FinancialDtos() {}

    // ─── Contract ────────────────────────────────────────────────────────────

    public record ContractDto(UUID id, UUID restaurantId, String contractNumber, BigDecimal commissionRate,
                              LocalDate startsAt, LocalDate endsAt, String status, Instant createdAt) {}

    public record ContractCreateDto(
        @NotNull UUID restaurantId,
        @NotBlank @Size(min = 1, max = 64) String contractNumber,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal commissionRate,
        @NotNull LocalDate startsAt,
        LocalDate endsAt
    ) {}

    public record ContractUpdateDto(
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal commissionRate,
        LocalDate endsAt,
        @Pattern(regexp = "^(draft|active|paused|terminated)$") @Size(min = 1, max = 64) String status
    ) {}

    // ─── Invoice ─────────────────────────────────────────────────────────────

    public record InvoiceDto(UUID id, UUID restaurantId, String invoiceNumber, LocalDate periodStart,
                             LocalDate periodEnd, BigDecimal subtotal, BigDecimal tvaAmount, BigDecimal totalTtc,
                             String status, LocalDate issuedAt, LocalDate dueAt, Instant paidAt, Instant createdAt) {}

    public record InvoiceCreateDto(
        @NotNull UUID restaurantId,
        @NotBlank @Size(min = 1, max = 64) String invoiceNumber,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd
    ) {}

    public record InvoiceUpdateDto(
        BigDecimal subtotal,
        BigDecimal tvaAmount,
        BigDecimal totalTtc,
        @Pattern(regexp = "^(draft|sent|paid|overdue|cancelled)$") @Size(min = 1, max = 64) String status,
        LocalDate issuedAt,
        LocalDate dueAt
    ) {}

    // ─── InvoiceLine ─────────────────────────────────────────────────────────

    public record InvoiceLineDto(UUID id, UUID invoiceId, String label, BigDecimal quantity,
                                 BigDecimal unitPrice, BigDecimal lineTotal, Integer sortOrder) {}

    public record InvoiceLineCreateDto(
        @NotNull UUID invoiceId,
        @NotBlank @Size(min = 1, max = 128) String label,
        @NotNull @DecimalMin("0.00") BigDecimal quantity,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
        Integer sortOrder
    ) {}

    // ─── WalletTransaction ───────────────────────────────────────────────────

    public record WalletTxDto(UUID id, UUID restaurantId, String type, BigDecimal amount,
                              BigDecimal balanceAfter, String reason, UUID referenceId, String referenceType,
                              Instant createdAt, UUID createdById) {}

    public record WalletTxCreateDto(
        @NotNull UUID restaurantId,
        @NotNull @Pattern(regexp = "^(credit|debit|commission|payout|adjustment)$") @Size(min = 1, max = 64) String type,
        @NotNull BigDecimal amount,
        @Size(min = 1, max = 1024) String reason,
        UUID referenceId,
        @Size(min = 1, max = 64) String referenceType
    ) {}

    // ─── ContractTemplate (V13) ──────────────────────────────────────────────

    /**
     * Template contractuel (PDF generation) — versionné + multilangue.
     *
     * @param tenantId {@code null} = template platform-wide (fallback global)
     * @param language ISO 639-1 ∈ {fr | en | ar}
     */
    public record ContractTemplateDto(
        UUID id, UUID tenantId, String code, String name, Integer version, String language,
        String title, String body, boolean active, Instant createdAt, Instant updatedAt
    ) {}

    public record ContractTemplateCreateDto(
        UUID tenantId,
        @NotBlank @Size(min = 1, max = 64) String code,
        @NotBlank @Size(min = 1, max = 128) String name,
        @jakarta.validation.constraints.Positive Integer version,
        @Pattern(regexp = "^(fr|en|ar)$") @Size(min = 1, max = 64) String language,
        @NotBlank @Size(min = 1, max = 128) String title,
        @NotBlank @Size(min = 1, max = 1024) String body,
        Boolean isActive
    ) {}

    public record ContractTemplatePatchDto(
        @Size(min = 1, max = 128) String name,
        @Size(min = 1, max = 128) String title,
        @Size(min = 1, max = 1024) String body,
        Boolean isActive
    ) {}
}
