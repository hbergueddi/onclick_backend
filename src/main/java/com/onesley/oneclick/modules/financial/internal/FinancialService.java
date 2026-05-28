package com.onesley.oneclick.modules.financial.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
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

import static com.onesley.oneclick.modules.financial.api.FinancialDtos.*;
import com.onesley.oneclick.modules.financial.api.FinancialDtos;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractCreateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractUpdateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.InvoiceCreateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.InvoiceDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.InvoiceLineCreateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.InvoiceLineDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.InvoiceUpdateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.WalletTxCreateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.WalletTxDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractTemplateCreateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractTemplateDto;
import com.onesley.oneclick.modules.financial.api.FinancialDtos.ContractTemplatePatchDto;
import com.onesley.oneclick.exception.BadRequestException;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FinancialService {

    private final ContractRepository contractRepo;
    private final InvoiceRepository invoiceRepo;
    private final InvoiceLineRepository lineRepo;
    private final WalletTransactionRepository walletRepo;
    private final ContractTemplateRepository templateRepo;
    private final ContractTemplateArticleRepository articleRepo;
    private final ContractDisabledArticleRepository disabledRepo;

    @PersistenceContext
    private EntityManager entityManager;

    // ─── Contracts ───────────────────────────────────────────────────────────

    public Page<ContractDto> findAllContracts(UUID restaurantId, String status, int page, int size) {
        Specification<Contract> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (restaurantId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("restaurantId"), restaurantId));
        if (status != null)       spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        return contractRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(Contract::toDto);
    }

    public ContractDto findContractById(UUID id) {
        Contract c = contractRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Contract", id));
        SecurityHelper.requireOwnerOrAdmin(c.getCreatedBy());
        return c.toDto();
    }

    @Transactional
    public ContractDto createContract(ContractCreateDto dto) {
        Contract c = new Contract(UUID.randomUUID(), dto.restaurantId(), dto.contractNumber(),
            dto.commissionRate(), dto.startsAt());
        if (dto.endsAt() != null) c.setEndsAt(dto.endsAt());
        if (dto.walletAdminRate() != null) c.setWalletAdminRate(dto.walletAdminRate());
        applyContractDetails(c, dto.details());
        return contractRepo.save(c).toDto();
    }

    @Transactional
    public ContractDto updateContract(UUID id, ContractUpdateDto dto) {
        Contract c = contractRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Contract", id));
        SecurityHelper.requireOwnerOrAdmin(c.getCreatedBy());
        if (dto.commissionRate() != null)  c.setCommissionRate(dto.commissionRate());
        if (dto.walletAdminRate() != null) c.setWalletAdminRate(dto.walletAdminRate());
        if (dto.endsAt() != null)          c.setEndsAt(dto.endsAt());
        if (dto.status() != null)          c.setStatus(dto.status());
        applyContractDetails(c, dto.details());
        return contractRepo.save(c).toDto();
    }

    /** Applique les champs legacy optionnels (V54) — null = inchangé (PATCH partiel). */
    private void applyContractDetails(Contract c, FinancialDtos.ContractDetailsDto d) {
        if (d == null) return;
        if (d.representedBy() != null)            c.setRepresentedBy(d.representedBy());
        if (d.representedTitle() != null)         c.setRepresentedTitle(d.representedTitle());
        if (d.oneclickCommissionRate() != null)   c.setOneclickCommissionRate(d.oneclickCommissionRate());
        if (d.paymentTerms() != null)             c.setPaymentTerms(d.paymentTerms());
        if (d.plafondCommissionMensuel() != null) c.setPlafondCommissionMensuel(d.plafondCommissionMensuel());
        if (d.autoRenew() != null)                c.setAutoRenew(d.autoRenew());
        if (d.signedAt() != null)                 c.setSignedAt(d.signedAt());
        if (d.raisonSociale() != null)            c.setRaisonSociale(d.raisonSociale());
        if (d.formeJuridique() != null)           c.setFormeJuridique(d.formeJuridique());
        if (d.numeroRc() != null)                 c.setNumeroRc(d.numeroRc());
        if (d.numeroIf() != null)                 c.setNumeroIf(d.numeroIf());
        if (d.numeroIce() != null)                c.setNumeroIce(d.numeroIce());
        if (d.capitalSocial() != null)            c.setCapitalSocial(d.capitalSocial());
        if (d.banque() != null)                   c.setBanque(d.banque());
        if (d.rib() != null)                      c.setRib(d.rib());
        if (d.capaciteCouverts() != null)         c.setCapaciteCouverts(d.capaciteCouverts());
        if (d.horairesExploitation() != null)     c.setHorairesExploitation(d.horairesExploitation());
        if (d.joursFermeture() != null)           c.setJoursFermeture(d.joursFermeture());
        if (d.dureeEngagementMois() != null)      c.setDureeEngagementMois(d.dureeEngagementMois());
        if (d.preavisResiliationMois() != null)   c.setPreavisResiliationMois(d.preavisResiliationMois());
        if (d.penaliteResiliation() != null)      c.setPenaliteResiliation(d.penaliteResiliation());
        if (d.lieuSignature() != null)            c.setLieuSignature(d.lieuSignature());
        if (d.nombreExemplaires() != null)        c.setNombreExemplaires(d.nombreExemplaires());
    }

    // ─── Invoices ────────────────────────────────────────────────────────────

    public Page<InvoiceDto> findAllInvoices(UUID restaurantId, String status, int page, int size) {
        Specification<Invoice> spec = (root, q, cb) -> cb.conjunction();
        if (restaurantId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("restaurantId"), restaurantId));
        if (status != null)       spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        return invoiceRepo.findAll(spec, PageRequest.of(page, size, Sort.by("periodStart").descending()))
            .map(Invoice::toDto);
    }

    public InvoiceDto findInvoiceById(UUID id) {
        Invoice i = invoiceRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Invoice", id));
        // TODO check ownership — Invoice n'a pas de createdBy direct, fallback restaurantId
        // (effectivement admin-only car restaurantId != userId courant)
        SecurityHelper.requireOwnerOrAdmin(i.getRestaurantId());
        return i.toDto();
    }

    @Transactional
    public InvoiceDto createInvoice(InvoiceCreateDto dto) {
        Invoice i = new Invoice(UUID.randomUUID(), dto.restaurantId(), dto.invoiceNumber(),
            dto.periodStart(), dto.periodEnd());
        return invoiceRepo.save(i).toDto();
    }

    @Transactional
    public InvoiceDto updateInvoice(UUID id, InvoiceUpdateDto dto) {
        Invoice i = invoiceRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Invoice", id));
        // TODO check ownership — Invoice n'a pas de createdBy direct, fallback restaurantId
        SecurityHelper.requireOwnerOrAdmin(i.getRestaurantId());
        if (dto.subtotal() != null)   i.setSubtotal(dto.subtotal());
        if (dto.tvaAmount() != null)  i.setTvaAmount(dto.tvaAmount());
        if (dto.totalTtc() != null)   i.setTotalTtc(dto.totalTtc());
        if (dto.issuedAt() != null)   i.setIssuedAt(dto.issuedAt());
        if (dto.dueAt() != null)      i.setDueAt(dto.dueAt());
        if (dto.status() != null) {
            if ("paid".equals(dto.status())) i.markPaid();
            else i.setStatus(dto.status());
        }
        return invoiceRepo.save(i).toDto();
    }

    // ─── Invoice lines ───────────────────────────────────────────────────────

    public List<InvoiceLineDto> findLinesByInvoice(UUID invoiceId) {
        Invoice i = invoiceRepo.findById(invoiceId)
            .orElseThrow(() -> new NotFoundException("Invoice", invoiceId));
        // TODO check ownership — Invoice n'a pas de createdBy direct, fallback restaurantId
        SecurityHelper.requireOwnerOrAdmin(i.getRestaurantId());
        return lineRepo.findAllByInvoiceId(invoiceId).stream().map(InvoiceLine::toDto).toList();
    }

    @Transactional
    public InvoiceLineDto createLine(InvoiceLineCreateDto dto) {
        Invoice invoiceRef = entityManager.getReference(Invoice.class, dto.invoiceId());
        InvoiceLine l = new InvoiceLine(UUID.randomUUID(), invoiceRef, dto.label(), dto.quantity(), dto.unitPrice());
        if (dto.sortOrder() != null) l.setSortOrder(dto.sortOrder());
        return lineRepo.save(l).toDto();
    }

    // ─── Wallet transactions ─────────────────────────────────────────────────

    public Page<WalletTxDto> findAllWalletTx(UUID restaurantId, String type, int page, int size) {
        Specification<WalletTransaction> spec = (root, q, cb) -> cb.conjunction();
        if (restaurantId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("restaurantId"), restaurantId));
        if (type != null)         spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), type));
        return walletRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(WalletTransaction::toDto);
    }

    @Transactional
    public WalletTxDto createWalletTx(WalletTxCreateDto dto) {
        WalletTransaction t = new WalletTransaction(UUID.randomUUID(), dto.restaurantId(),
            dto.type(), dto.amount(), dto.reason());
        if (dto.referenceId() != null)   t.setReferenceId(dto.referenceId());
        if (dto.referenceType() != null) t.setReferenceType(dto.referenceType());
        return walletRepo.save(t).toDto();
    }

    // ─── Contract templates (V13) ────────────────────────────────────────────

    /**
     * Liste les templates non supprimés — filtres optionnels par tenant, langue
     * et statut. Pagination/tri non requis sur ce volume (≤ qq dizaines de
     * templates par tenant).
     */
    public List<ContractTemplateDto> findAllContractTemplates(UUID tenantId, String language, Boolean isActive) {
        Specification<ContractTemplate> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (tenantId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("tenantId"), tenantId));
        if (language != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("language"), language));
        if (isActive != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("active"), isActive));
        return templateRepo.findAll(spec, Sort.by("code").ascending().and(Sort.by("version").descending()))
            .stream().map(ContractTemplate::toDto).toList();
    }

    public ContractTemplateDto findContractTemplateById(UUID id) {
        return templateRepo.findById(id)
            .filter(t -> t.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ContractTemplate", id))
            .toDto();
    }

    /**
     * Résolution par code fonctionnel — utilisé par {@code ContractDownload}
     * (génération PDF). Si aucun template tenant-spécifique n'existe, retombe
     * automatiquement sur la version platform-wide ({@code tenantId IS NULL}).
     *
     * @param tenantId tenant courant (non null) — fallback platform si manquant
     * @param code     code fonctionnel (ex: {@code "partner_contract"})
     * @param language ISO 639-1 — par défaut {@code "fr"}
     * @param version  numéro de version — par défaut {@code 1}
     */
    public ContractTemplateDto findContractTemplateByCode(UUID tenantId, String code, String language, Integer version) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("code est requis");
        }
        String lang = (language != null && !language.isBlank()) ? language : "fr";
        Integer ver = (version != null) ? version : 1;

        // 1. Tentative tenant-spécifique
        if (tenantId != null) {
            var t = templateRepo
                .findByTenantIdAndCodeAndLanguageAndVersionAndActiveTrueAndDeletedAtIsNull(
                    tenantId, code, lang, ver);
            if (t.isPresent()) return t.get().toDto();
        }
        // 2. Fallback platform-wide
        return templateRepo
            .findByTenantIdIsNullAndCodeAndLanguageAndVersionAndActiveTrueAndDeletedAtIsNull(code, lang, ver)
            .orElseThrow(() -> new NotFoundException(
                "ContractTemplate", "code=" + code + " lang=" + lang + " v=" + ver))
            .toDto();
    }

    @Transactional
    public ContractTemplateDto createContractTemplate(ContractTemplateCreateDto dto) {
        ContractTemplate t = new ContractTemplate(
            UUID.randomUUID(), dto.tenantId(), dto.code(), dto.name(),
            dto.version(), dto.language(), dto.title(), dto.body()
        );
        if (dto.isActive() != null) t.setActive(dto.isActive());
        return templateRepo.save(t).toDto();
    }

    @Transactional
    public ContractTemplateDto patchContractTemplate(UUID id, ContractTemplatePatchDto dto) {
        ContractTemplate t = templateRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ContractTemplate", id));
        if (dto.name() != null)     t.setName(dto.name());
        if (dto.title() != null)    t.setTitle(dto.title());
        if (dto.body() != null)     t.setBody(dto.body());
        if (dto.isActive() != null) t.setActive(dto.isActive());
        return templateRepo.save(t).toDto();
    }

    @Transactional
    public void softDeleteContractTemplate(UUID id) {
        ContractTemplate t = templateRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ContractTemplate", id));
        t.markDeleted();
        templateRepo.save(t);
    }

    // ─── Contract template articles (clauses, V55) ─────────────────────────────

    public List<ContractTemplateArticleDto> findTemplateArticles(UUID templateId) {
        return articleRepo.findAllByTemplateIdOrderBySortOrderAscArticleNumberAsc(templateId)
            .stream().map(ContractTemplateArticle::toDto).toList();
    }

    @Transactional
    public ContractTemplateArticleDto createTemplateArticle(UUID templateId, ContractTemplateArticleCreateDto dto) {
        // garde-fou : le template parent doit exister (non supprimé)
        templateRepo.findById(templateId)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ContractTemplate", templateId));
        ContractTemplateArticle a = new ContractTemplateArticle(
            UUID.randomUUID(), templateId, dto.articleNumber(), dto.title(), dto.content(), dto.sortOrder());
        return articleRepo.save(a).toDto();
    }

    @Transactional
    public ContractTemplateArticleDto patchTemplateArticle(UUID articleId, ContractTemplateArticlePatchDto dto) {
        ContractTemplateArticle a = articleRepo.findById(articleId)
            .orElseThrow(() -> new NotFoundException("ContractTemplateArticle", articleId));
        if (dto.articleNumber() != null) a.setArticleNumber(dto.articleNumber());
        if (dto.title() != null)         a.setTitle(dto.title());
        if (dto.content() != null)       a.setContent(dto.content());
        if (dto.sortOrder() != null)     a.setSortOrder(dto.sortOrder());
        return articleRepo.save(a).toDto();
    }

    @Transactional
    public void deleteTemplateArticle(UUID articleId) {
        if (!articleRepo.existsById(articleId)) {
            throw new NotFoundException("ContractTemplateArticle", articleId);
        }
        articleRepo.deleteById(articleId);
    }

    // ─── Articles désactivés par contrat (V56) + renouvellement (V57) ──────────

    public List<UUID> findDisabledArticleIds(UUID contractId) {
        return disabledRepo.findAllByContractId(contractId).stream()
            .map(ContractDisabledArticle::getArticleId).toList();
    }

    @Transactional
    public List<UUID> setDisabledArticles(UUID contractId, List<UUID> articleIds) {
        contractRepo.findById(contractId)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Contract", contractId));
        disabledRepo.deleteByContractId(contractId);
        if (articleIds != null) {
            articleIds.stream().distinct().forEach(aid ->
                disabledRepo.save(new ContractDisabledArticle(UUID.randomUUID(), contractId, aid)));
        }
        return findDisabledArticleIds(contractId);
    }

    /**
     * Renouvelle les contrats actifs en auto-renew dont l'échéance tombe dans les 30 jours
     * (ou est dépassée) — port de l'edge function renew-contracts. Étend ends_at de la durée
     * d'engagement (défaut 12 mois) et incrémente renewal_number.
     */
    @Transactional
    public ContractRenewResultDto renewContracts() {
        java.time.LocalDate threshold = java.time.LocalDate.now().plusDays(30);
        List<Contract> renewable = contractRepo.findRenewable(threshold);
        for (Contract c : renewable) {
            int months = (c.getDureeEngagementMois() != null && c.getDureeEngagementMois() > 0)
                ? c.getDureeEngagementMois() : 12;
            c.setEndsAt(c.getEndsAt().plusMonths(months));
            c.setRenewalNumber(c.getRenewalNumber() + 1);
            contractRepo.save(c);
        }
        return new ContractRenewResultDto(renewable.size());
    }
}
