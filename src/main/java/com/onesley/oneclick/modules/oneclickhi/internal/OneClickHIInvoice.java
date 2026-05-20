package com.onesley.oneclick.modules.oneclickhi.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "oneclick_hi_invoices")
@Getter
public class OneClickHIInvoice extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id")
    @Setter private UUID tenantId;

    @Column(name = "restaurant_id")
    @Setter private UUID restaurantId;

    @Column(name = "invoice_number", unique = true, length = 64)
    @Setter private String invoiceNumber;

    @Column(name = "period_month", nullable = false, length = 64)
    @Setter private String periodMonth;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    @Setter private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "vat_amount", nullable = false, precision = 12, scale = 2)
    @Setter private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 64)
    @Setter private String status = "draft";

    @Column(name = "pdf_url", length = 512)
    @Setter private String pdfUrl;

    @Column(name = "deleted_at")
    @Setter private Instant deletedAt;

    // ─── V24 — Sprint K : workflow validation / envoi facture ────────────────

    @Column(name = "credit_3pct", precision = 12, scale = 2)
    @Setter private BigDecimal credit3pct;

    @Column(name = "validated_at")
    @Setter private Instant validatedAt;

    @Column(name = "validated_by")
    @Setter private UUID validatedBy;

    @Column(name = "sent_at")
    @Setter private Instant sentAt;

    @Column(name = "pdf_path", length = 512)
    @Setter private String pdfPath;
}
