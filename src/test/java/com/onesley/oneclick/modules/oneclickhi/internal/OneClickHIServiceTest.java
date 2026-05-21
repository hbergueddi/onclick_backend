package com.onesley.oneclick.modules.oneclickhi.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.oneclickhi.api.OneClickHIDtos.OneClickHIInvoiceCreateDto;
import com.onesley.oneclick.modules.oneclickhi.api.OneClickHIDtos.OneClickHIInvoicePatchDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class OneClickHIServiceTest {

    @Mock OneClickHIInvoiceRepository invoiceRepo;
    @Mock EntityManager em;
    @Mock Query query;
    @InjectMocks OneClickHIService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "em", em);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(invoiceRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private OneClickHIInvoice invoice() {
        OneClickHIInvoice i = new OneClickHIInvoice();
        i.setInvoiceNumber("INV-1"); i.setStatus("draft");
        return i;
    }

    @Test
    void findAll_threeBranches() {
        when(invoiceRepo.findByRestaurant(any())).thenReturn(List.of(invoice()));
        when(invoiceRepo.findByTenant(any())).thenReturn(List.of(invoice()));
        when(invoiceRepo.findAllActive()).thenReturn(List.of(invoice(), invoice()));
        assertThat(service.findAll(UUID.randomUUID(), null)).hasSize(1);
        assertThat(service.findAll(null, UUID.randomUUID())).hasSize(1);
        assertThat(service.findAll(null, null)).hasSize(2);
    }

    @Test
    void findById_notFoundAndFound() {
        when(invoiceRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        OneClickHIInvoice i = invoice();
        when(invoiceRepo.findById(i.getId())).thenReturn(Optional.of(i));
        assertThat(service.findById(i.getId())).isNotNull();
    }

    @Test
    void create_nullAmounts_defaultZero() {
        assertThat(service.create(new OneClickHIInvoiceCreateDto(
            UUID.randomUUID(), UUID.randomUUID(), "INV-1", "2026-05", null, null, "http://pdf", null))).isNotNull();
        assertThat(service.create(new OneClickHIInvoiceCreateDto(
            UUID.randomUUID(), UUID.randomUUID(), "INV-2", "2026-05", new BigDecimal("100"), new BigDecimal("20"), null, new BigDecimal("3")))).isNotNull();
    }

    @Test
    void patch_notFoundAndSuccess() {
        when(invoiceRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patch(UUID.randomUUID(),
            new OneClickHIInvoicePatchDto("paid", null, null, null, null, null, null, null, null))).isInstanceOf(NotFoundException.class);
        OneClickHIInvoice i = invoice();
        when(invoiceRepo.findById(i.getId())).thenReturn(Optional.of(i));
        service.patch(i.getId(), new OneClickHIInvoicePatchDto("paid", new BigDecimal("100"), new BigDecimal("20"),
            "http://pdf", new BigDecimal("3"), Instant.now(), UUID.randomUUID(), Instant.now(), "/path"));
        assertThat(i.getStatus()).isEqualTo("paid");
    }

    @Test
    void softDelete_notFoundAndSuccess() {
        when(invoiceRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDelete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        OneClickHIInvoice i = invoice();
        when(invoiceRepo.findById(i.getId())).thenReturn(Optional.of(i));
        service.softDelete(i.getId());
        assertThat(i.getDeletedAt()).isNotNull();
    }

    @Test
    void cockpit_mapsRow() {
        Object[] row = { 5L, 1L, 2L, 1L, 1L, new BigDecimal("1000"), new BigDecimal("200"), 3L };
        when(query.getSingleResult()).thenReturn(row);
        assertThat(service.cockpit().totalInvoices()).isEqualTo(5L);
    }

    @Test
    void restaurantHI_mapsRow() {
        Object[] row = { 3L, new BigDecimal("500"), new BigDecimal("300"), new BigDecimal("200"), Instant.now() };
        when(query.getSingleResult()).thenReturn(row);
        assertThat(service.restaurantHI(UUID.randomUUID()).invoicesCount()).isEqualTo(3L);
    }

    @Test
    void restaurantHICharts_mapsRows() {
        Object[] row = { "2026-05", new BigDecimal("500"), 2 };
        when(query.getResultList()).thenReturn(Collections.singletonList(row));
        assertThat(service.restaurantHICharts(UUID.randomUUID(), 12)).hasSize(1);
    }
}
