package com.onesley.oneclick.modules.payment.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import com.onesley.oneclick.modules.payment.internal.Payment;
import com.onesley.oneclick.modules.payment.internal.PaymentMethod;
import com.onesley.oneclick.modules.payment.internal.PaymentTransaction;
import com.onesley.oneclick.modules.payment.internal.Refund;

public final class PaymentDtos {

    private PaymentDtos() {}

    // ─── PaymentMethod ───────────────────────────────────────────────────────

    public record PaymentMethodDto(UUID id, UUID userId, String type, String last4, String provider,
                                   boolean isDefault, LocalDate expiresAt, Instant createdAt) {
        public static PaymentMethodDto from(PaymentMethod m) {
            return new PaymentMethodDto(m.getId(), m.getUserId(), m.getType(), m.getLast4(),
                m.getProvider(), m.isDefault(), m.getExpiresAt(), m.getCreatedAt());
        }
    }

    public record PaymentMethodCreateDto(
        @NotNull UUID userId,
        @NotNull @Pattern(regexp = "^(card|bank_account|wallet|cash_on_site)$") String type,
        String last4,
        String provider,
        String providerToken,
        LocalDate expiresAt,
        Boolean isDefault
    ) {}

    // ─── Payment ─────────────────────────────────────────────────────────────

    public record PaymentDto(UUID id, UUID userId, UUID paymentMethodId, BigDecimal amount, String currency,
                             String status, String provider, String transactionRef, String referenceType,
                             UUID referenceId, Instant completedAt, Instant createdAt) {
        public static PaymentDto from(Payment p) {
            return new PaymentDto(p.getId(), p.getUserId(), p.getPaymentMethodId(), p.getAmount(),
                p.getCurrency(), p.getStatus(), p.getProvider(), p.getTransactionRef(),
                p.getReferenceType(), p.getReferenceId(), p.getCompletedAt(), p.getCreatedAt());
        }
    }

    public record PaymentCreateDto(
        @NotNull UUID userId,
        UUID paymentMethodId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String currency,
        String provider,
        String transactionRef,
        String referenceType,
        UUID referenceId
    ) {}

    public record PaymentUpdateDto(
        @Pattern(regexp = "^(pending|processing|succeeded|failed|cancelled|refunded)$") String status,
        String transactionRef
    ) {}

    // ─── Refund ──────────────────────────────────────────────────────────────

    public record RefundDto(UUID id, UUID paymentId, BigDecimal amount, String reason, String status,
                            Instant createdAt, Instant processedAt, UUID createdById) {
        public static RefundDto from(Refund r) {
            return new RefundDto(r.getId(), r.getPaymentId(), r.getAmount(), r.getReason(), r.getStatus(),
                r.getCreatedAt(), r.getProcessedAt(), r.getCreatedById());
        }
    }

    public record RefundCreateDto(
        @NotNull UUID paymentId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String reason
    ) {}

    public record RefundUpdateDto(
        @Pattern(regexp = "^(pending|succeeded|failed)$") String status
    ) {}

    // ─── PaymentTransaction (provider events log) ────────────────────────────

    public record TransactionDto(UUID id, UUID paymentId, String eventType, Map<String, Object> providerResponse,
                                 Instant createdAt) {
        public static TransactionDto from(PaymentTransaction t) {
            return new TransactionDto(t.getId(), t.getPaymentId(), t.getEventType(),
                t.getProviderResponse(), t.getCreatedAt());
        }
    }

    public record TransactionCreateDto(
        @NotNull UUID paymentId,
        @NotBlank String eventType,
        Map<String, Object> providerResponse
    ) {}
}
