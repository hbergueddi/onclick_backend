package com.onesley.oneclick.modules.payment;

import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.PageResponse;
import com.onesley.oneclick.modules.payment.internal.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.payment.api.PaymentDtos.*;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Méthodes, paiements, remboursements, événements provider (§12)")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    // Bug 32 (Batch C RBAC v2) — double-binding sur tous les endpoints du module payment.

    // ─── Payment methods ─────────────────────────────────────────────────────

    @GetMapping("/methods/by-user/{userId}")
    @PreAuthorize("isAuthenticated() or hasAuthority('VIEW:PAYMENTS')")
    public List<PaymentMethodDto> findMethodsByUser(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return service.findMethodsByUser(userId);
    }

    @PostMapping("/methods")
    @PreAuthorize("isAuthenticated() or hasAuthority('CREATE:PAYMENTS')")
    public ResponseEntity<PaymentMethodDto> createMethod(@Valid @RequestBody PaymentMethodCreateDto dto) {
        SecurityHelper.requireOwnerOrAdmin(dto.userId());
        PaymentMethodDto m = service.createMethod(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }

    @DeleteMapping("/methods/{id}")
    @PreAuthorize("isAuthenticated() or hasAuthority('DELETE:PAYMENTS')")
    public ResponseEntity<Void> deleteMethod(@PathVariable UUID id) {
        service.softDeleteMethod(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Payments ────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Paiements paginés — filtres userId / status")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR') or hasAuthority('VIEW:PAYMENTS')")
    public PageResponse<PaymentDto> findAll(
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAllPayments(userId, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated() or hasAuthority('VIEW:PAYMENTS')")
    public PaymentDto findById(@PathVariable UUID id) { return service.findPaymentById(id); }

    @PostMapping
    @PreAuthorize("isAuthenticated() or hasAuthority('CREATE:PAYMENTS')")
    public ResponseEntity<PaymentDto> create(@Valid @RequestBody PaymentCreateDto dto) {
        SecurityHelper.requireOwnerOrAdmin(dto.userId());
        PaymentDto p = service.createPayment(dto);
        return ResponseEntity.created(URI.create("/api/payments/" + p.id())).body(p);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Mise à jour status. status=succeeded → completed_at automatique.")
    @PreAuthorize("isAuthenticated() or hasAuthority('UPDATE:PAYMENTS')")
    public PaymentDto update(@PathVariable UUID id, @Valid @RequestBody PaymentUpdateDto dto) {
        return service.updatePayment(id, dto);
    }

    // ─── Refunds ─────────────────────────────────────────────────────────────

    @GetMapping("/{paymentId}/refunds")
    @PreAuthorize("isAuthenticated() or hasAuthority('VIEW:PAYMENTS')")
    public List<RefundDto> findRefundsByPayment(@PathVariable UUID paymentId) {
        return service.findRefundsByPayment(paymentId);
    }

    @PostMapping("/refunds")
    @PreAuthorize("isAuthenticated() or hasAuthority('CREATE:PAYMENTS')")
    public ResponseEntity<RefundDto> createRefund(@Valid @RequestBody RefundCreateDto dto) {
        RefundDto r = service.createRefund(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(r);
    }

    @PatchMapping("/refunds/{id}")
    @PreAuthorize("isAuthenticated() or hasAuthority('UPDATE:PAYMENTS')")
    public RefundDto updateRefund(@PathVariable UUID id, @Valid @RequestBody RefundUpdateDto dto) {
        return service.updateRefund(id, dto);
    }

    // ─── Provider events log ─────────────────────────────────────────────────

    @GetMapping("/{paymentId}/transactions")
    @Operation(summary = "Journal des événements provider (Stripe webhook, CMI callback…)")
    @PreAuthorize("isAuthenticated() or hasAuthority('VIEW:PAYMENTS')")
    public List<TransactionDto> findTxByPayment(@PathVariable UUID paymentId) {
        return service.findTxByPayment(paymentId);
    }

    @PostMapping("/transactions")
    @PreAuthorize("isAuthenticated() or hasAuthority('CREATE:PAYMENTS')")
    public ResponseEntity<TransactionDto> recordTx(@Valid @RequestBody TransactionCreateDto dto) {
        TransactionDto t = service.recordTx(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(t);
    }
}
