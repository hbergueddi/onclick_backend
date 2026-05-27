package com.onesley.oneclick.modules.financial.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractCreateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractTemplateCreateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractTemplatePatchDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractUpdateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.InvoiceCreateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.InvoiceLineCreateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.InvoiceUpdateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.WalletTxCreateDto;
import com.onesley.oneclick.security.SecurityHelper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link FinancialService} (L3 — modules.financial).
 * Couvre contrats, factures (dont markPaid), lignes, wallet tx, et templates
 * (résolution par code + fallback platform). Les contrôles d'accès statiques
 * {@code SecurityHelper.requireOwnerOrAdmin} sont neutralisés via mockStatic.
 */
@ExtendWith(MockitoExtension.class)
class FinancialServiceTest {

    @Mock ContractRepository contractRepo;
    @Mock InvoiceRepository invoiceRepo;
    @Mock InvoiceLineRepository lineRepo;
    @Mock WalletTransactionRepository walletRepo;
    @Mock ContractTemplateRepository templateRepo;
    @Mock EntityManager entityManager;
    @InjectMocks FinancialService service;

    @BeforeEach
    void injectEm() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    private Contract contract() {
        return new Contract(UUID.randomUUID(), UUID.randomUUID(), "C-1", new BigDecimal("3.00"), LocalDate.now());
    }
    private Invoice invoice() {
        return new Invoice(UUID.randomUUID(), UUID.randomUUID(), "INV-1", LocalDate.now(), LocalDate.now().plusMonths(1));
    }
    private ContractTemplate template() {
        return new ContractTemplate(UUID.randomUUID(), UUID.randomUUID(), "partner_contract", "Nom", 1, "fr", "Titre", "Corps");
    }

    // ─── Contracts ─────────────────────────────────────────────────────────────

    @Test
    void createContract_withEndsAt_andWithout() {
        when(contractRepo.save(any(Contract.class))).thenAnswer(i -> i.getArgument(0));
        // walletAdminRate explicite (1.50) → persisté
        var withRate = service.createContract(new ContractCreateDto(
            UUID.randomUUID(), "C-1", new BigDecimal("3.00"), new BigDecimal("1.50"), LocalDate.now(), LocalDate.now().plusYears(1)));
        assertThat(withRate.walletAdminRate()).isEqualByComparingTo("1.50");
        // walletAdminRate null → défaut entité 2.00 (V49)
        var defaulted = service.createContract(new ContractCreateDto(
            UUID.randomUUID(), "C-2", new BigDecimal("5.00"), null, LocalDate.now(), null));
        assertThat(defaulted.walletAdminRate()).isEqualByComparingTo("2.00");
    }

    @Test
    void findContractById_notFound_throwsNotFound() {
        when(contractRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findContractById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void findContractById_found_returnsDto() {
        Contract c = contract();
        when(contractRepo.findById(any())).thenReturn(Optional.of(c));
        try (MockedStatic<SecurityHelper> ignored = mockStatic(SecurityHelper.class)) {
            assertThat(service.findContractById(c.getId())).isNotNull();
        }
    }

    @Test
    void updateContract_updatesFields() {
        Contract c = contract();
        when(contractRepo.findById(any())).thenReturn(Optional.of(c));
        when(contractRepo.save(any(Contract.class))).thenAnswer(i -> i.getArgument(0));
        try (MockedStatic<SecurityHelper> ignored = mockStatic(SecurityHelper.class)) {
            service.updateContract(c.getId(), new ContractUpdateDto(new BigDecimal("7.50"), new BigDecimal("2.50"), LocalDate.now().plusYears(2), "active"));
        }
        assertThat(c.getCommissionRate()).isEqualByComparingTo("7.50");
        assertThat(c.getWalletAdminRate()).isEqualByComparingTo("2.50");
        assertThat(c.getStatus()).isEqualTo("active");
    }

    @Test
    void updateContract_notFound_throwsNotFound() {
        when(contractRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateContract(UUID.randomUUID(),
            new ContractUpdateDto(null, null, null, null))).isInstanceOf(NotFoundException.class);
    }

    // ─── Invoices ──────────────────────────────────────────────────────────────

    @Test
    void createInvoice_success() {
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.createInvoice(new InvoiceCreateDto(
            UUID.randomUUID(), "INV-1", LocalDate.now(), LocalDate.now().plusMonths(1)))).isNotNull();
    }

    @Test
    void updateInvoice_paidStatus_marksPaid() {
        Invoice i = invoice();
        when(invoiceRepo.findById(any())).thenReturn(Optional.of(i));
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(x -> x.getArgument(0));
        try (MockedStatic<SecurityHelper> ignored = mockStatic(SecurityHelper.class)) {
            service.updateInvoice(i.getId(), new InvoiceUpdateDto(
                new BigDecimal("100.00"), new BigDecimal("20.00"), new BigDecimal("120.00"),
                "paid", LocalDate.now(), LocalDate.now().plusDays(30)));
        }
        assertThat(i.getStatus()).isEqualTo("paid");
        assertThat(i.getSubtotal()).isEqualByComparingTo("100.00");
    }

    @Test
    void updateInvoice_otherStatus_setsStatusWithoutMarkPaid() {
        Invoice i = invoice();
        when(invoiceRepo.findById(any())).thenReturn(Optional.of(i));
        when(invoiceRepo.save(any(Invoice.class))).thenAnswer(x -> x.getArgument(0));
        try (MockedStatic<SecurityHelper> ignored = mockStatic(SecurityHelper.class)) {
            service.updateInvoice(i.getId(), new InvoiceUpdateDto(null, null, null, "sent", null, null));
        }
        assertThat(i.getStatus()).isEqualTo("sent");
    }

    @Test
    void findInvoiceById_notFound_throwsNotFound() {
        when(invoiceRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findInvoiceById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    // ─── Invoice lines ───────────────────────────────────────────────────────────

    @Test
    void createLine_withSortOrder_andWithout() {
        when(entityManager.getReference(eq(Invoice.class), any())).thenReturn(invoice());
        when(lineRepo.save(any(InvoiceLine.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.createLine(new InvoiceLineCreateDto(
            UUID.randomUUID(), "Commission", new BigDecimal("1"), new BigDecimal("66.30"), 2))).isNotNull();
        assertThat(service.createLine(new InvoiceLineCreateDto(
            UUID.randomUUID(), "TVA", new BigDecimal("1"), new BigDecimal("13.26"), null))).isNotNull();
    }

    @Test
    void findLinesByInvoice_found_mapsLines() {
        Invoice i = invoice();
        when(invoiceRepo.findById(any())).thenReturn(Optional.of(i));
        when(lineRepo.findAllByInvoiceId(any())).thenReturn(List.of(
            new InvoiceLine(UUID.randomUUID(), i, "L1", new BigDecimal("1"), new BigDecimal("10"))));
        try (MockedStatic<SecurityHelper> ignored = mockStatic(SecurityHelper.class)) {
            assertThat(service.findLinesByInvoice(i.getId())).hasSize(1);
        }
    }

    // ─── Wallet tx ───────────────────────────────────────────────────────────────

    @Test
    void createWalletTx_withAndWithoutReferences() {
        when(walletRepo.save(any(WalletTransaction.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.createWalletTx(new WalletTxCreateDto(
            UUID.randomUUID(), "commission", new BigDecimal("44.20"), "scan", UUID.randomUUID(), "scan"))).isNotNull();
        assertThat(service.createWalletTx(new WalletTxCreateDto(
            UUID.randomUUID(), "credit", new BigDecimal("10.00"), null, null, null))).isNotNull();
    }

    // ─── Contract templates ──────────────────────────────────────────────────────

    @Test
    void findContractTemplateByCode_blankCode_throwsBadRequest() {
        assertThatThrownBy(() -> service.findContractTemplateByCode(UUID.randomUUID(), "  ", "fr", 1))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void findContractTemplateByCode_tenantSpecificFound() {
        UUID tid = UUID.randomUUID();
        when(templateRepo.findByTenantIdAndCodeAndLanguageAndVersionAndActiveTrueAndDeletedAtIsNull(
            eq(tid), eq("partner_contract"), eq("fr"), eq(1))).thenReturn(Optional.of(template()));
        assertThat(service.findContractTemplateByCode(tid, "partner_contract", "fr", 1)).isNotNull();
    }

    @Test
    void findContractTemplateByCode_fallsBackToPlatform() {
        UUID tid = UUID.randomUUID();
        when(templateRepo.findByTenantIdAndCodeAndLanguageAndVersionAndActiveTrueAndDeletedAtIsNull(
            any(), any(), any(), any())).thenReturn(Optional.empty());
        when(templateRepo.findByTenantIdIsNullAndCodeAndLanguageAndVersionAndActiveTrueAndDeletedAtIsNull(
            eq("partner_contract"), eq("fr"), eq(1))).thenReturn(Optional.of(template()));
        // langue null -> défaut "fr", version null -> défaut 1
        assertThat(service.findContractTemplateByCode(tid, "partner_contract", null, null)).isNotNull();
    }

    @Test
    void findContractTemplateByCode_notFound_throwsNotFound() {
        when(templateRepo.findByTenantIdIsNullAndCodeAndLanguageAndVersionAndActiveTrueAndDeletedAtIsNull(
            any(), any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findContractTemplateByCode(null, "missing", "en", 2))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findContractTemplateById_foundAndNotFound() {
        ContractTemplate t = template();
        when(templateRepo.findById(t.getId())).thenReturn(Optional.of(t));
        assertThat(service.findContractTemplateById(t.getId())).isNotNull();
        UUID missing = UUID.randomUUID();
        when(templateRepo.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findContractTemplateById(missing)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void createContractTemplate_withIsActive() {
        when(templateRepo.save(any(ContractTemplate.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.createContractTemplate(new ContractTemplateCreateDto(
            UUID.randomUUID(), "code", "name", 1, "fr", "titre", "corps", false))).isNotNull();
    }

    @Test
    void patchContractTemplate_updatesFields() {
        ContractTemplate t = template();
        when(templateRepo.findById(any())).thenReturn(Optional.of(t));
        when(templateRepo.save(any(ContractTemplate.class))).thenAnswer(i -> i.getArgument(0));
        service.patchContractTemplate(t.getId(), new ContractTemplatePatchDto("Nouveau", "NewTitre", "NewCorps", true));
        assertThat(t.getName()).isEqualTo("Nouveau");
        assertThat(t.getTitle()).isEqualTo("NewTitre");
    }

    @Test
    void softDeleteContractTemplate_marksDeleted() {
        ContractTemplate t = template();
        when(templateRepo.findById(any())).thenReturn(Optional.of(t));
        service.softDeleteContractTemplate(t.getId());
        assertThat(t.getDeletedAt()).isNotNull();
        verify(templateRepo).save(t);
    }

    // ─── findAll* delegations ──────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void findAll_methods_delegateToRepositories() {
        when(contractRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        when(invoiceRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        when(walletRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        when(templateRepo.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        assertThat(service.findAllContracts(UUID.randomUUID(), "active", 0, 20).getContent()).isEmpty();
        assertThat(service.findAllInvoices(UUID.randomUUID(), "paid", 0, 20).getContent()).isEmpty();
        assertThat(service.findAllWalletTx(UUID.randomUUID(), "credit", 0, 20).getContent()).isEmpty();
        assertThat(service.findAllContractTemplates(UUID.randomUUID(), "fr", true)).isEmpty();
    }
}
