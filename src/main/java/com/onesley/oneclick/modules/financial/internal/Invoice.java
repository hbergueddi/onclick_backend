package com.onesley.oneclick.modules.financial.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.InvoiceDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Facture mensuelle par restaurant. */
@Entity
@Table(name = "invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @NotBlank
    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;

    @NotNull
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @NotNull
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    @Setter private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tva_amount", nullable = false, precision = 12, scale = 2)
    @Setter private BigDecimal tvaAmount = BigDecimal.ZERO;

    @Column(name = "total_ttc", nullable = false, precision = 12, scale = 2)
    @Setter private BigDecimal totalTtc = BigDecimal.ZERO;

    @Pattern(regexp = "^(draft|sent|paid|overdue|cancelled)$")
    @Column(name = "status", nullable = false)
    @Setter private String status = "draft";

    @Column(name = "issued_at")
    @Setter private LocalDate issuedAt;

    @Column(name = "due_at")
    @Setter private LocalDate dueAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    public Invoice(UUID id, UUID restaurantId, String invoiceNumber, LocalDate periodStart, LocalDate periodEnd) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.invoiceNumber = invoiceNumber;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }
    public void markPaid() { this.paidAt = Instant.now(); this.status = "paid"; }

    /** Mapping vers le DTO public exposé hors du module. */
    public InvoiceDto toDto() {
        return new InvoiceDto(id, restaurantId, invoiceNumber, periodStart, periodEnd, subtotal,
            tvaAmount, totalTtc, status, issuedAt, dueAt, paidAt, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((Invoice) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
