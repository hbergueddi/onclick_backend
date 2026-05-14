package com.onesley.oneclick.modules.oneclickhi.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oneclick_hi_invoices")
public class OneClickHIInvoice extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "restaurant_id")
    private UUID restaurantId;

    @Column(name = "invoice_number", unique = true, length = 64)
    private String invoiceNumber;

    @Column(name = "period_month", nullable = false, length = 10)
    private String periodMonth;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "vat_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 32)
    private String status = "draft";

    @Column(name = "pdf_url")
    private String pdfUrl;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // ─── V24 — Sprint K : workflow validation / envoi facture ────────────────

    @Column(name = "credit_3pct", precision = 12, scale = 2)
    private BigDecimal credit3pct;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "validated_by")
    private UUID validatedBy;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "pdf_path")
    private String pdfPath;

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public UUID getRestaurantId() { return restaurantId; }
    public void setRestaurantId(UUID v) { this.restaurantId = v; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String v) { this.invoiceNumber = v; }
    public String getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(String v) { this.periodMonth = v; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
    public BigDecimal getVatAmount() { return vatAmount; }
    public void setVatAmount(BigDecimal v) { this.vatAmount = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String v) { this.pdfUrl = v; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant v) { this.deletedAt = v; }
    public BigDecimal getCredit3pct() { return credit3pct; }
    public void setCredit3pct(BigDecimal v) { this.credit3pct = v; }
    public Instant getValidatedAt() { return validatedAt; }
    public void setValidatedAt(Instant v) { this.validatedAt = v; }
    public UUID getValidatedBy() { return validatedBy; }
    public void setValidatedBy(UUID v) { this.validatedBy = v; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant v) { this.sentAt = v; }
    public String getPdfPath() { return pdfPath; }
    public void setPdfPath(String v) { this.pdfPath = v; }
}
