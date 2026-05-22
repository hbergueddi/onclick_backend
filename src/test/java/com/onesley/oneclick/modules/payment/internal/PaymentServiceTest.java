package com.onesley.oneclick.modules.payment.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.PaymentCreateDto;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.PaymentMethodCreateDto;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.PaymentUpdateDto;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.RefundCreateDto;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.RefundUpdateDto;
import com.onesley.oneclick.security.SecurityHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests unitaires Mockito de {@link PaymentService} (L3 — modules.payment). */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock PaymentMethodRepository methodRepo;
    @Mock PaymentRepository paymentRepo;
    @Mock RefundRepository refundRepo;
    @Mock PaymentTransactionRepository txRepo;
    @InjectMocks PaymentService service;

    @BeforeEach
    void setup() {
        lenient().when(methodRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(paymentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(refundRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(txRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private PaymentMethod method() { return new PaymentMethod(UUID.randomUUID(), UUID.randomUUID(), "card"); }
    private Payment payment() { return new Payment(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100")); }
    private Refund refund(UUID paymentId) { return new Refund(UUID.randomUUID(), paymentId, new BigDecimal("10")); }

    // ─── Methods ───────────────────────────────────────────────────────────────

    @Test
    void methods_find_create_softDelete() {
        when(methodRepo.findAllByUserId(any())).thenReturn(List.of(method()));
        assertThat(service.findMethodsByUser(UUID.randomUUID())).hasSize(1);
        assertThat(service.createMethod(new PaymentMethodCreateDto(UUID.randomUUID(), "card", "4242", "stripe", "tok", LocalDate.now().plusYears(1), true))).isNotNull();
        assertThat(service.createMethod(new PaymentMethodCreateDto(UUID.randomUUID(), "wallet", null, null, null, null, null))).isNotNull();

        when(methodRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDeleteMethod(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        PaymentMethod m = method();
        when(methodRepo.findById(m.getId())).thenReturn(Optional.of(m));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            service.softDeleteMethod(m.getId());
            assertThat(m.getDeletedAt()).isNotNull();
        }
    }

    // ─── Payments ────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void payments_findAll_findById_create_update() {
        when(paymentRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAllPayments(UUID.randomUUID(), "succeeded", 0, 20).getContent()).isEmpty();
        assertThat(service.findAllPayments(null, null, 0, 20).getContent()).isEmpty();

        when(paymentRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.findPaymentById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        }
        Payment p = payment();
        when(paymentRepo.findById(p.getId())).thenReturn(Optional.of(p));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.findPaymentById(p.getId())).isNotNull();
        }

        assertThat(service.createPayment(new PaymentCreateDto(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100"), "MAD", "stripe", "ref", "reservation", UUID.randomUUID()))).isNotNull();
        assertThat(service.createPayment(new PaymentCreateDto(UUID.randomUUID(), null, new BigDecimal("50"), null, null, null, null, null))).isNotNull();

        // update : notFound, succeeded (markCompleted), autre statut
        when(paymentRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.updatePayment(UUID.randomUUID(), new PaymentUpdateDto("succeeded", null)))
                .isInstanceOf(NotFoundException.class);
        }
        Payment p2 = payment();
        when(paymentRepo.findById(p2.getId())).thenReturn(Optional.of(p2));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            service.updatePayment(p2.getId(), new PaymentUpdateDto("succeeded", "txref"));
            service.updatePayment(p2.getId(), new PaymentUpdateDto("failed", null));
        }
    }

    // ─── Refunds ─────────────────────────────────────────────────────────────

    @Test
    void refunds_find_create_update() {
        Payment p = payment();
        when(paymentRepo.findById(p.getId())).thenReturn(Optional.of(p));
        when(refundRepo.findAllByPaymentId(p.getId())).thenReturn(List.of(refund(p.getId())));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.findRefundsByPayment(p.getId())).hasSize(1);
            assertThat(service.createRefund(new RefundCreateDto(p.getId(), new BigDecimal("10"), "L4"))).isNotNull();
        }
        // payment not found
        when(paymentRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.findRefundsByPayment(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> service.createRefund(new RefundCreateDto(UUID.randomUUID(), new BigDecimal("5"), null))).isInstanceOf(NotFoundException.class);
        }
        // updateRefund : refund not found ; success (succeeded → markProcessed)
        when(refundRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateRefund(UUID.randomUUID(), new RefundUpdateDto("succeeded"))).isInstanceOf(NotFoundException.class);
        Payment parent = payment();
        Refund r = refund(parent.getId());
        when(refundRepo.findById(r.getId())).thenReturn(Optional.of(r));
        when(paymentRepo.findById(parent.getId())).thenReturn(Optional.of(parent));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.updateRefund(r.getId(), new RefundUpdateDto("succeeded"))).isNotNull();
        }
    }

    // ─── Transactions ──────────────────────────────────────────────────────────

    @Test
    void transactions_findByPayment() {
        Payment p = payment();
        when(paymentRepo.findById(p.getId())).thenReturn(Optional.of(p));
        when(txRepo.findAllByPaymentId(p.getId())).thenReturn(List.of());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.findTxByPayment(p.getId())).isEmpty();
        }
    }
}
