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

/** Facture mensuelle par restaurant. */
@Entity
@Table(name = "invoices")
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
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tva_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal tvaAmount = BigDecimal.ZERO;

    @Column(name = "total_ttc", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalTtc = BigDecimal.ZERO;

    @Pattern(regexp = "^(draft|sent|paid|overdue|cancelled)$")
    @Column(name = "status", nullable = false)
    private String status = "draft";

    @Column(name = "issued_at")
    private LocalDate issuedAt;

    @Column(name = "due_at")
    private LocalDate dueAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected Invoice() {}

    public Invoice(UUID id, UUID restaurantId, String invoiceNumber, LocalDate periodStart, LocalDate periodEnd) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.invoiceNumber = invoiceNumber;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getTvaAmount() { return tvaAmount; }
    public void setTvaAmount(BigDecimal tvaAmount) { this.tvaAmount = tvaAmount; }
    public BigDecimal getTotalTtc() { return totalTtc; }
    public void setTotalTtc(BigDecimal totalTtc) { this.totalTtc = totalTtc; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDate issuedAt) { this.issuedAt = issuedAt; }
    public LocalDate getDueAt() { return dueAt; }
    public void setDueAt(LocalDate dueAt) { this.dueAt = dueAt; }
    public Instant getPaidAt() { return paidAt; }
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
