package com.onesley.oneclick.modules.financial.internal;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Ligne de facture (1 row par item). line_total = quantity × unit_price (GENERATED ALWAYS). */
@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "invoice_id", nullable = false, insertable = false, updatable = false)
    private UUID invoiceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @NotBlank
    @Column(name = "label", nullable = false)
    private String label;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity = BigDecimal.ONE;

    @NotNull
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    // GENERATED ALWAYS AS (quantity * unit_price) STORED — Hibernate read-only
    @Column(name = "line_total", insertable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    protected InvoiceLine() {}

    public InvoiceLine(UUID id, Invoice invoice, String label, BigDecimal quantity, BigDecimal unitPrice) {
        this.id = id;
        this.invoice = invoice;
        this.label = label;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public Invoice getInvoice() { return invoice; }
    public String getLabel() { return label; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((InvoiceLine) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
