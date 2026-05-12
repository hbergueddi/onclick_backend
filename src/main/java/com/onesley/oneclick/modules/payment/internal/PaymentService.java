package com.onesley.oneclick.modules.payment.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.payment.api.PaymentDtos.*;
import com.onesley.oneclick.modules.payment.api.PaymentDtos;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.PaymentCreateDto;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.PaymentDto;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.PaymentMethodCreateDto;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.PaymentMethodDto;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.PaymentUpdateDto;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.RefundCreateDto;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.RefundDto;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.RefundUpdateDto;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.TransactionCreateDto;
import com.onesley.oneclick.modules.payment.api.PaymentDtos.TransactionDto;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentMethodRepository methodRepo;
    private final PaymentRepository paymentRepo;
    private final RefundRepository refundRepo;
    private final PaymentTransactionRepository txRepo;

    public PaymentService(PaymentMethodRepository methodRepo,
                          PaymentRepository paymentRepo,
                          RefundRepository refundRepo,
                          PaymentTransactionRepository txRepo) {
        this.methodRepo = methodRepo;
        this.paymentRepo = paymentRepo;
        this.refundRepo = refundRepo;
        this.txRepo = txRepo;
    }

    // ─── Payment methods ─────────────────────────────────────────────────────

    public List<PaymentMethodDto> findMethodsByUser(UUID userId) {
        return methodRepo.findAllByUserId(userId).stream()
            .filter(m -> m.getDeletedAt() == null)
            .map(PaymentMethodDto::from)
            .toList();
    }

    @Transactional
    public PaymentMethodDto createMethod(PaymentMethodCreateDto dto) {
                PaymentMethod m = new PaymentMethod(UUID.randomUUID(), dto.userId(), dto.type());
        if (dto.last4() != null)         m.setLast4(dto.last4());
        if (dto.provider() != null)      m.setProvider(dto.provider());
        if (dto.providerToken() != null) m.setProviderToken(dto.providerToken());
        if (dto.expiresAt() != null)     m.setExpiresAt(dto.expiresAt());
        if (Boolean.TRUE.equals(dto.isDefault())) m.setDefault(true);
        return PaymentMethodDto.from(methodRepo.save(m));
    }

    @Transactional
    public void softDeleteMethod(UUID id) {
        PaymentMethod m = methodRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("PaymentMethod", id));
        SecurityHelper.requireOwnerOrAdmin(m.getUserId());
        m.markDeleted();
        methodRepo.save(m);
    }

    // ─── Payments ────────────────────────────────────────────────────────────

    public Page<PaymentDto> findAllPayments(UUID userId, String status, int page, int size) {
        Specification<Payment> spec = (root, q, cb) -> cb.conjunction();
        if (userId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("userId"), userId));
        if (status != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        return paymentRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(PaymentDto::from);
    }

    public PaymentDto findPaymentById(UUID id) {
        Payment p = paymentRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Payment", id));
        SecurityHelper.requireOwnerOrAdmin(p.getUserId());
        return PaymentDto.from(p);
    }

    @Transactional
    public PaymentDto createPayment(PaymentCreateDto dto) {
                Payment p = new Payment(UUID.randomUUID(), dto.userId(), dto.amount());
        if (dto.paymentMethodId() != null) {
            p.setPaymentMethodId(dto.paymentMethodId());
        }
        if (dto.currency() != null)       p.setCurrency(dto.currency());
        if (dto.provider() != null)       p.setProvider(dto.provider());
        if (dto.transactionRef() != null) p.setTransactionRef(dto.transactionRef());
        if (dto.referenceType() != null)  p.setReferenceType(dto.referenceType());
        if (dto.referenceId() != null)    p.setReferenceId(dto.referenceId());
        return PaymentDto.from(paymentRepo.save(p));
    }

    @Transactional
    public PaymentDto updatePayment(UUID id, PaymentUpdateDto dto) {
        Payment p = paymentRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Payment", id));
        SecurityHelper.requireOwnerOrAdmin(p.getUserId());
        if (dto.status() != null) {
            if ("succeeded".equals(dto.status())) p.markCompleted();
            else p.setStatus(dto.status());
        }
        if (dto.transactionRef() != null) p.setTransactionRef(dto.transactionRef());
        return PaymentDto.from(paymentRepo.save(p));
    }

    // ─── Refunds ─────────────────────────────────────────────────────────────

    public List<RefundDto> findRefundsByPayment(UUID paymentId) {
        Payment p = paymentRepo.findById(paymentId)
            .orElseThrow(() -> new NotFoundException("Payment", paymentId));
        SecurityHelper.requireOwnerOrAdmin(p.getUserId());
        return refundRepo.findAllByPaymentId(paymentId).stream().map(RefundDto::from).toList();
    }

    @Transactional
    public RefundDto createRefund(RefundCreateDto dto) {
        Payment p = paymentRepo.findById(dto.paymentId())
            .orElseThrow(() -> new NotFoundException("Payment", dto.paymentId()));
        SecurityHelper.requireOwnerOrAdmin(p.getUserId());
        Refund r = new Refund(UUID.randomUUID(), dto.paymentId(), dto.amount());
        if (dto.reason() != null) r.setReason(dto.reason());
        return RefundDto.from(refundRepo.save(r));
    }

    @Transactional
    public RefundDto updateRefund(UUID id, RefundUpdateDto dto) {
        Refund r = refundRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Refund", id));
        // Check ownership via le payment parent (refund.createdById n'est pas toujours rempli)
        Payment parentPayment = paymentRepo.findById(r.getPaymentId())
            .orElseThrow(() -> new NotFoundException("Payment", r.getPaymentId()));
        SecurityHelper.requireOwnerOrAdmin(parentPayment.getUserId());
        if (dto.status() != null) {
            r.setStatus(dto.status());
            if ("succeeded".equals(dto.status()) || "failed".equals(dto.status())) {
                r.markProcessed();
            }
        }
        return RefundDto.from(refundRepo.save(r));
    }

    // ─── Provider transactions log ───────────────────────────────────────────

    public List<TransactionDto> findTxByPayment(UUID paymentId) {
        Payment p = paymentRepo.findById(paymentId)
            .orElseThrow(() -> new NotFoundException("Payment", paymentId));
        SecurityHelper.requireOwnerOrAdmin(p.getUserId());
        return txRepo.findAllByPaymentId(paymentId).stream().map(TransactionDto::from).toList();
    }

    @Transactional
    public TransactionDto recordTx(TransactionCreateDto dto) {
        
        PaymentTransaction t = new PaymentTransaction(UUID.randomUUID(), dto.paymentId(),
            dto.eventType(), dto.providerResponse());
        return TransactionDto.from(txRepo.save(t));
    }
}
