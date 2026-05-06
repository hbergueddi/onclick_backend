package com.onesley.oneclick.entity.contract;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entité {@code public.invoice_lines} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : created_at + updated_at hérités.
 */
@Entity
@Table(name = "invoice_lines")
public class InvoiceLine extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "description")
    private String description;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price_ht", nullable = false)
    private BigDecimal unitPriceHt;

    @Column(name = "total_ht")
    private BigDecimal totalHt;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    protected InvoiceLine() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPriceHt() { return unitPriceHt; }
    public BigDecimal getTotalHt() { return totalHt; }
    public Integer getSortOrder() { return sortOrder; }
}
