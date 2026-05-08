package com.onesley.oneclick.entity.contract;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Entité {@code public.invoice_lines} — lignes d'une facture (label, quantité,
 * prix HT, total HT, sort_order pour rendu PDF).
 *
 * <h3>Jointures JPA (passe 3) — aggregate member de {@link OneclickHiInvoice}</h3>
 * <ul>
 *   <li>{@code invoice_id NOT NULL} → {@link OneclickHiInvoice} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 * </ul>
 */
@Entity
@Table(name = "invoice_lines")
public class InvoiceLine extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "invoice_id", nullable = false, insertable = false, updatable = false)
    private UUID invoiceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private OneclickHiInvoice invoice;

    @NotBlank
    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "description")
    private String description;

    @NotNull
    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @NotNull
    @Column(name = "unit_price_ht", nullable = false)
    private BigDecimal unitPriceHt;

    @Column(name = "total_ht")
    private BigDecimal totalHt;

    @NotNull
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    protected InvoiceLine() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public OneclickHiInvoice getInvoice() { return invoice; }
    /** Package-private : appelé par les helpers du parent {@link OneclickHiInvoice#addLine}/{@code removeLine}. */
    void setInvoice(OneclickHiInvoice invoice) { this.invoice = invoice; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPriceHt() { return unitPriceHt; }
    public BigDecimal getTotalHt() { return totalHt; }
    public Integer getSortOrder() { return sortOrder; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        InvoiceLine that = (InvoiceLine) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
