package com.onesley.oneclick.modules.oneclickhi.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.oneclickhi.api.OneClickHIDtos.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class OneClickHIService {

    private static java.time.Instant toInstant(Object o) {
        if (o == null) return null;
        if (o instanceof java.time.Instant i) return i;
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (o instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return java.time.Instant.parse(o.toString());
    }

    private final OneClickHIInvoiceRepository invoiceRepo;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public List<OneClickHIInvoiceDto> findAll(UUID restaurantId, UUID tenantId) {
        if (restaurantId != null) return invoiceRepo.findByRestaurant(restaurantId).stream().map(OneClickHIInvoiceDto::from).toList();
        if (tenantId != null) return invoiceRepo.findByTenant(tenantId).stream().map(OneClickHIInvoiceDto::from).toList();
        return invoiceRepo.findAllActive().stream().map(OneClickHIInvoiceDto::from).toList();
    }

    @Transactional(readOnly = true)
    public OneClickHIInvoiceDto findById(UUID id) {
        return OneClickHIInvoiceDto.from(invoiceRepo.findById(id).orElseThrow(() -> new NotFoundException("OneClickHIInvoice", id)));
    }

    public OneClickHIInvoiceDto create(OneClickHIInvoiceCreateDto dto) {
        OneClickHIInvoice i = new OneClickHIInvoice();
        i.setTenantId(dto.tenantId());
        i.setRestaurantId(dto.restaurantId());
        i.setInvoiceNumber(dto.invoiceNumber());
        i.setPeriodMonth(dto.periodMonth());
        i.setTotalAmount(dto.totalAmount() == null ? BigDecimal.ZERO : dto.totalAmount());
        i.setVatAmount(dto.vatAmount() == null ? BigDecimal.ZERO : dto.vatAmount());
        i.setPdfUrl(dto.pdfUrl());
        i.setCredit3pct(dto.credit3pct());
        return OneClickHIInvoiceDto.from(invoiceRepo.save(i));
    }

    public OneClickHIInvoiceDto patch(UUID id, OneClickHIInvoicePatchDto dto) {
        OneClickHIInvoice i = invoiceRepo.findById(id).orElseThrow(() -> new NotFoundException("OneClickHIInvoice", id));
        if (dto.status() != null) i.setStatus(dto.status());
        if (dto.totalAmount() != null) i.setTotalAmount(dto.totalAmount());
        if (dto.vatAmount() != null) i.setVatAmount(dto.vatAmount());
        if (dto.pdfUrl() != null) i.setPdfUrl(dto.pdfUrl());
        if (dto.credit3pct() != null) i.setCredit3pct(dto.credit3pct());
        if (dto.validatedAt() != null) i.setValidatedAt(dto.validatedAt());
        if (dto.validatedBy() != null) i.setValidatedBy(dto.validatedBy());
        if (dto.sentAt() != null) i.setSentAt(dto.sentAt());
        if (dto.pdfPath() != null) i.setPdfPath(dto.pdfPath());
        return OneClickHIInvoiceDto.from(invoiceRepo.save(i));
    }

    public void softDelete(UUID id) {
        OneClickHIInvoice i = invoiceRepo.findById(id).orElseThrow(() -> new NotFoundException("OneClickHIInvoice", id));
        i.setDeletedAt(Instant.now());
        invoiceRepo.save(i);
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public OneClickHICockpitDto cockpit() {
        Object[] row = (Object[]) em.createNativeQuery("""
            SELECT
              COUNT(*),
              COUNT(*) FILTER (WHERE status = 'draft'),
              COUNT(*) FILTER (WHERE status = 'sent'),
              COUNT(*) FILTER (WHERE status = 'paid'),
              COUNT(*) FILTER (WHERE status = 'overdue'),
              COALESCE(SUM(total_amount), 0),
              COALESCE(SUM(total_amount) FILTER (WHERE period_month = to_char(NOW(), 'YYYY-MM')), 0),
              COUNT(DISTINCT restaurant_id)
              FROM oneclick_hi_invoices
             WHERE deleted_at IS NULL
            """).getSingleResult();
        return new OneClickHICockpitDto(
            ((Number) row[0]).longValue(),
            ((Number) row[1]).longValue(),
            ((Number) row[2]).longValue(),
            ((Number) row[3]).longValue(),
            ((Number) row[4]).longValue(),
            (BigDecimal) row[5],
            (BigDecimal) row[6],
            ((Number) row[7]).longValue()
        );
    }

    @Transactional(readOnly = true)
    public RestaurantHIDto restaurantHI(UUID restaurantId) {
        Object[] row = (Object[]) em.createNativeQuery("""
            SELECT
              COUNT(*),
              COALESCE(SUM(total_amount), 0),
              COALESCE(SUM(total_amount) FILTER (WHERE status = 'paid'), 0),
              COALESCE(SUM(total_amount) FILTER (WHERE status IN ('sent','overdue')), 0),
              MAX(created_at)
              FROM oneclick_hi_invoices
             WHERE restaurant_id = :rid AND deleted_at IS NULL
            """).setParameter("rid", restaurantId).getSingleResult();
        return new RestaurantHIDto(
            restaurantId,
            ((Number) row[0]).longValue(),
            (BigDecimal) row[1],
            (BigDecimal) row[2],
            (BigDecimal) row[3],
            row[4] != null ? toInstant(row[4]) : null
        );
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<RestaurantHIChartPointDto> restaurantHICharts(UUID restaurantId, int months) {
        List<Object[]> rows = em.createNativeQuery("""
            SELECT period_month, COALESCE(SUM(total_amount), 0), COUNT(*)
              FROM oneclick_hi_invoices
             WHERE restaurant_id = :rid AND deleted_at IS NULL
             GROUP BY period_month
             ORDER BY period_month DESC
             LIMIT :limit
            """)
            .setParameter("rid", restaurantId)
            .setParameter("limit", months)
            .getResultList();
        return rows.stream().map(r -> new RestaurantHIChartPointDto(
            (String) r[0],
            (BigDecimal) r[1],
            ((Number) r[2]).intValue()
        )).toList();
    }
}
