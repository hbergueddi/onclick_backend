package com.onesley.oneclick.entity.contract;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.entity.restaurant.Restaurant;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.BatchSize;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Entité {@code public.oneclick_hi_invoices} — factures OneClick Hospitality
 * Intelligence (commission mensuelle restaurant : 3% + 2% wallet).
 *
 * <h3>Jointures JPA (passe 3) — aggregate root</h3>
 * <ul>
 *   <li>{@code restaurant_id NOT NULL} → {@link Restaurant} en {@code @ManyToOne(LAZY)}, optional=false.</li>
 *   <li>{@code @OneToMany lines} (InvoiceLine) cascade ALL + orphanRemoval —
 *       lignes de facture appartiennent strictement à la facture (rendu PDF).</li>
 *   <li>{@code validated_by} : audit field, reste UUID brut.</li>
 * </ul>
 */
@Entity
@Table(name = "oneclick_hi_invoices")
public class OneclickHiInvoice extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, insertable = false, updatable = false)
    private UUID restaurantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

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

    /** Audit field : UUID brut. */
    @Column(name = "validated_by")
    private UUID validatedBy;

    @Column(name = "sent_at")
    private Instant sentAt;

    // ─── Aggregate member : lines (cascade ALL + orphanRemoval) ─────────────
    @OneToMany(mappedBy = "invoice", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private Set<InvoiceLine> lines = new HashSet<>();

    protected OneclickHiInvoice() {
        // JPA
    }

    public UUID getId() { return id; }
    public UUID getRestaurantId() { return restaurantId; }
    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }
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
    public Set<InvoiceLine> getLines() { return lines; }

    public void addLine(InvoiceLine l) {
        lines.add(l);
        l.setInvoice(this);
    }

    public void removeLine(InvoiceLine l) {
        lines.remove(l);
        l.setInvoice(null);
    }

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
        OneclickHiInvoice that = (OneclickHiInvoice) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
