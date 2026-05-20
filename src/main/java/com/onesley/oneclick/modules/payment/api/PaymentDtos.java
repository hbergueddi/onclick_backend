package com.onesley.oneclick.modules.payment.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs publics du module paiement.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Entity.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 */
public final class PaymentDtos {

    private PaymentDtos() {}

    // ─── PaymentMethod ───────────────────────────────────────────────────────

    public record PaymentMethodDto(UUID id, UUID userId, String type, String last4, String provider,
                                   boolean isDefault, LocalDate expiresAt, Instant createdAt) {}

    public record PaymentMethodCreateDto(
        @NotNull UUID userId,
        @NotNull @Pattern(regexp = "^(card|bank_account|wallet|cash_on_site)$") @Size(min = 1, max = 64) String type,
        @Size(min = 1, max = 64) String last4,
        @Size(min = 1, max = 64) String provider,
        @Size(min = 1, max = 512) String providerToken,
        LocalDate expiresAt,
        Boolean isDefault
    ) {}

    // ─── Payment ─────────────────────────────────────────────────────────────

    public record PaymentDto(UUID id, UUID userId, UUID paymentMethodId, BigDecimal amount, String currency,
                             String status, String provider, String transactionRef, String referenceType,
                             UUID referenceId, Instant completedAt, Instant createdAt) {}

    public record PaymentCreateDto(
        @NotNull UUID userId,
        UUID paymentMethodId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @Size(min = 1, max = 64) String currency,
        @Size(min = 1, max = 64) String provider,
        @Size(min = 1, max = 64) String transactionRef,
        @Size(min = 1, max = 64) String referenceType,
        UUID referenceId
    ) {}

    public record PaymentUpdateDto(
        @Pattern(regexp = "^(pending|processing|succeeded|failed|cancelled|refunded)$") @Size(min = 1, max = 64) String status,
        @Size(min = 1, max = 64) String transactionRef
    ) {}

    // ─── Refund ──────────────────────────────────────────────────────────────

    public record RefundDto(UUID id, UUID paymentId, BigDecimal amount, String reason, String status,
                            Instant createdAt, Instant processedAt, UUID createdById) {}

    public record RefundCreateDto(
        @NotNull UUID paymentId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @Size(min = 1, max = 1024) String reason
    ) {}

    public record RefundUpdateDto(
        @Pattern(regexp = "^(pending|succeeded|failed)$") @Size(min = 1, max = 64) String status
    ) {}

    // ─── PaymentTransaction (provider events log) ────────────────────────────

    public record TransactionDto(UUID id, UUID paymentId, String eventType, Map<String, Object> providerResponse,
                                 Instant createdAt) {}

    public record TransactionCreateDto(
        @NotNull UUID paymentId,
        @NotBlank @Size(min = 1, max = 64) String eventType,
        Map<String, Object> providerResponse
    ) {}
}
