package com.onesley.oneclick.modules.financial;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.Restaurant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.financial.FinancialDtos.*;

@Service
@Transactional(readOnly = true)
public class FinancialService {

    private final ContractRepository contractRepo;
    private final InvoiceRepository invoiceRepo;
    private final InvoiceLineRepository lineRepo;
    private final WalletTransactionRepository walletRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public FinancialService(ContractRepository contractRepo,
                            InvoiceRepository invoiceRepo,
                            InvoiceLineRepository lineRepo,
                            WalletTransactionRepository walletRepo) {
        this.contractRepo = contractRepo;
        this.invoiceRepo = invoiceRepo;
        this.lineRepo = lineRepo;
        this.walletRepo = walletRepo;
    }

    // ─── Contracts ───────────────────────────────────────────────────────────

    public Page<ContractDto> findAllContracts(UUID restaurantId, String status, int page, int size) {
        Specification<Contract> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (restaurantId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("restaurantId"), restaurantId));
        if (status != null)       spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        return contractRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(ContractDto::from);
    }

    public ContractDto findContractById(UUID id) {
        return ContractDto.from(contractRepo.findById(id)
            .filter(c -> c.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Contract", id)));
    }

    @Transactional
    public ContractDto createContract(ContractCreateDto dto) {
        Restaurant restaurantRef = entityManager.getReference(Restaurant.class, dto.restaurantId());
        Contract c = new Contract(UUID.randomUUID(), restaurantRef, dto.contractNumber(),
            dto.commissionRate(), dto.startsAt());
        if (dto.endsAt() != null) c.setEndsAt(dto.endsAt());
        return ContractDto.from(contractRepo.save(c));
    }

    @Transactional
    public ContractDto updateContract(UUID id, ContractUpdateDto dto) {
        Contract c = contractRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Contract", id));
        if (dto.commissionRate() != null) c.setCommissionRate(dto.commissionRate());
        if (dto.endsAt() != null)         c.setEndsAt(dto.endsAt());
        if (dto.status() != null)         c.setStatus(dto.status());
        return ContractDto.from(contractRepo.save(c));
    }

    // ─── Invoices ────────────────────────────────────────────────────────────

    public Page<InvoiceDto> findAllInvoices(UUID restaurantId, String status, int page, int size) {
        Specification<Invoice> spec = (root, q, cb) -> cb.conjunction();
        if (restaurantId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("restaurantId"), restaurantId));
        if (status != null)       spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        return invoiceRepo.findAll(spec, PageRequest.of(page, size, Sort.by("periodStart").descending()))
            .map(InvoiceDto::from);
    }

    public InvoiceDto findInvoiceById(UUID id) {
        return InvoiceDto.from(invoiceRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Invoice", id)));
    }

    @Transactional
    public InvoiceDto createInvoice(InvoiceCreateDto dto) {
        Restaurant restaurantRef = entityManager.getReference(Restaurant.class, dto.restaurantId());
        Invoice i = new Invoice(UUID.randomUUID(), restaurantRef, dto.invoiceNumber(),
            dto.periodStart(), dto.periodEnd());
        return InvoiceDto.from(invoiceRepo.save(i));
    }

    @Transactional
    public InvoiceDto updateInvoice(UUID id, InvoiceUpdateDto dto) {
        Invoice i = invoiceRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Invoice", id));
        if (dto.subtotal() != null)   i.setSubtotal(dto.subtotal());
        if (dto.tvaAmount() != null)  i.setTvaAmount(dto.tvaAmount());
        if (dto.totalTtc() != null)   i.setTotalTtc(dto.totalTtc());
        if (dto.issuedAt() != null)   i.setIssuedAt(dto.issuedAt());
        if (dto.dueAt() != null)      i.setDueAt(dto.dueAt());
        if (dto.status() != null) {
            if ("paid".equals(dto.status())) i.markPaid();
            else i.setStatus(dto.status());
        }
        return InvoiceDto.from(invoiceRepo.save(i));
    }

    // ─── Invoice lines ───────────────────────────────────────────────────────

    public List<InvoiceLineDto> findLinesByInvoice(UUID invoiceId) {
        return lineRepo.findAllByInvoiceId(invoiceId).stream().map(InvoiceLineDto::from).toList();
    }

    @Transactional
    public InvoiceLineDto createLine(InvoiceLineCreateDto dto) {
        Invoice invoiceRef = entityManager.getReference(Invoice.class, dto.invoiceId());
        InvoiceLine l = new InvoiceLine(UUID.randomUUID(), invoiceRef, dto.label(), dto.quantity(), dto.unitPrice());
        if (dto.sortOrder() != null) l.setSortOrder(dto.sortOrder());
        return InvoiceLineDto.from(lineRepo.save(l));
    }

    // ─── Wallet transactions ─────────────────────────────────────────────────

    public Page<WalletTxDto> findAllWalletTx(UUID restaurantId, String type, int page, int size) {
        Specification<WalletTransaction> spec = (root, q, cb) -> cb.conjunction();
        if (restaurantId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("restaurantId"), restaurantId));
        if (type != null)         spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), type));
        return walletRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(WalletTxDto::from);
    }

    @Transactional
    public WalletTxDto createWalletTx(WalletTxCreateDto dto) {
        Restaurant restaurantRef = entityManager.getReference(Restaurant.class, dto.restaurantId());
        WalletTransaction t = new WalletTransaction(UUID.randomUUID(), restaurantRef,
            dto.type(), dto.amount(), dto.reason());
        if (dto.referenceId() != null)   t.setReferenceId(dto.referenceId());
        if (dto.referenceType() != null) t.setReferenceType(dto.referenceType());
        return WalletTxDto.from(walletRepo.save(t));
    }
}
