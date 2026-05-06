package com.onesley.oneclick.entity.contract;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entité {@code public.oneclick_hi_invoices} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "oneclick_hi_invoices")
public class OneclickHiInvoice extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @NotNull
    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @NotNull
    @Column(name = "total_ca", nullable = false)
    private BigDecimal totalCa;

    @NotNull
    @Column(name = "credit_3pct", nullable = false)
    private BigDecimal credit3pct;

    @NotNull
    @Column(name = "commission_2pct", nullable = false)
    private BigDecimal commission2pct;

    @NotBlank
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @NotNull
    @Column(name = "tva_rate", nullable = false)
    private BigDecimal tvaRate;

    @NotNull
    @Column(name = "tva_amount", nullable = false)
    private BigDecimal tvaAmount;

    @NotNull
    @Column(name = "total_ttc", nullable = false)
    private BigDecimal totalTtc;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "pdf_path")
    private String pdfPath;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "validated_by")
    private UUID validatedBy;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OneclickHiInvoice() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public LocalDate getPeriodMonth() { return periodMonth; }
    public BigDecimal getTotalCa() { return totalCa; }
    public BigDecimal getCredit3pct() { return credit3pct; }
    public BigDecimal getCommission2pct() { return commission2pct; }
    public String getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getReminderSentAt() { return reminderSentAt; }
    public String getNotes() { return notes; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public BigDecimal getTvaRate() { return tvaRate; }
    public BigDecimal getTvaAmount() { return tvaAmount; }
    public BigDecimal getTotalTtc() { return totalTtc; }
    public LocalDate getDueDate() { return dueDate; }
    public String getPdfPath() { return pdfPath; }
    public Instant getValidatedAt() { return validatedAt; }
    public UUID getValidatedBy() { return validatedBy; }
    public Instant getSentAt() { return sentAt; }
}
