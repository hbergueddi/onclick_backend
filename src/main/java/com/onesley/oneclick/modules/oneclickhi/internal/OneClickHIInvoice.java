package com.onesley.oneclick.modules.oneclickhi.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

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
    @Setter @Size(max = 64) private String invoiceNumber;

    @Column(name = "period_month", nullable = false, length = 10)
    @Setter @Size(max = 10) @NotBlank private String periodMonth;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    @Setter private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "vat_amount", nullable = false, precision = 12, scale = 2)
    @Setter private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 32)
    @Setter @Size(max = 32) @NotBlank @Pattern(regexp = "^(draft|sent|paid|overdue)$") private String status = "draft";

    @Column(name = "pdf_url")
    @Setter @Size(max = 1024) private String pdfUrl;

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

    @Column(name = "pdf_path")
    @Setter @Size(max = 1024) private String pdfPath;
}
