package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.GainRuleRequestDto;
import com.onesley.oneclick.security.SecurityHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service du workflow d'approbation des demandes de règles de gain (Sprint G.2.3).
 *
 * <p>3 actions principales :
 * <ol>
 *   <li>{@link #create} — restaurateur soumet une demande {@code status='pending'}</li>
 *   <li>{@link #approve} — admin approuve → crée la {@link GainRule} (désactivée)
 *     + met {@code status='approved'} + {@code reviewedBy/At} + {@code createdRuleId}</li>
 *   <li>{@link #reject} — admin refuse → {@code status='rejected'} + {@code rejectionReason}</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class GainRuleRequestService {

    private final GainRuleRequestRepository repository;
    private final GainRuleRepository gainRuleRepository;

    public GainRuleRequestService(GainRuleRequestRepository repository,
                                  GainRuleRepository gainRuleRepository) {
        this.repository = repository;
        this.gainRuleRepository = gainRuleRepository;
    }

    /** Liste toutes les demandes (admin platform-wide). */
    public List<GainRuleRequestDto> findAll() {
        return repository.findAll().stream()
            .filter(r -> r.getDeletedAt() == null)
            .map(GainRuleRequest::toDto)
            .toList();
    }

    /** Demandes en attente — admin dashboard. */
    public List<GainRuleRequestDto> findPending() {
        return repository.findAllByStatusAndDeletedAtIsNull("pending").stream()
            .map(GainRuleRequest::toDto)
            .toList();
    }

    /** Demandes d'un restaurant (restaurateur consulte ses demandes). */
    public List<GainRuleRequestDto> findByRestaurant(UUID restaurantId) {
        return repository.findAllByRestaurantIdAndDeletedAtIsNull(restaurantId).stream()
            .map(GainRuleRequest::toDto)
            .toList();
    }

    public GainRuleRequestDto findById(UUID id) {
        return repository.findById(id)
            .filter(r -> r.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("GainRuleRequest", id))
            .toDto();
    }

    /** Création d'une demande par un restaurateur. */
    @Transactional
    public GainRuleRequestDto create(GainRuleRequestDto.CreateDto dto) {
        GainRuleRequest req = new GainRuleRequest(
            UUID.randomUUID(),
            dto.restaurantId(),
            dto.name(),
            dto.conversionRate()
        );
        req.setDescription(dto.description());
        if (dto.type() != null) req.setType(dto.type());
        req.setCapPerVisit(dto.capPerVisit());
        req.setCapPerMonth(dto.capPerMonth());
        if (dto.minAmount() != null) req.setMinAmount(dto.minAmount());
        return repository.save(req).toDto();
    }

    /**
     * Approuve une demande — crée la {@link GainRule} (désactivée par défaut,
     * à activer manuellement par le restaurateur après revue).
     */
    @Transactional
    public GainRuleRequestDto approve(UUID requestId) {
        GainRuleRequest req = repository.findById(requestId)
            .filter(r -> r.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("GainRuleRequest", requestId));

        if (!"pending".equals(req.getStatus())) {
            throw new BadRequestException(
                "Demande déjà traitée (status=" + req.getStatus() + ")");
        }

        // 1. Crée la GainRule (désactivée — restaurateur l'active après revue)
        GainRule rule = new GainRule(UUID.randomUUID(), req.getRestaurantId(), req.getConversionRate());
        rule.setCapPerVisit(req.getCapPerVisit());
        rule.setCapPerMonth(req.getCapPerMonth());
        rule.setMinAmount(req.getMinAmount());
        rule.setActive(false);
        GainRule savedRule = gainRuleRepository.save(rule);

        // 2. Marque la demande approuvée + référence la rule créée
        req.setStatus("approved");
        req.setReviewedById(SecurityHelper.currentUserId());
        req.setReviewedAt(Instant.now());
        req.setCreatedRuleId(savedRule.getId());

        return repository.save(req).toDto();
    }

    /** Refus d'une demande avec motif obligatoire. */
    @Transactional
    public GainRuleRequestDto reject(UUID requestId, GainRuleRequestDto.RejectDto dto) {
        GainRuleRequest req = repository.findById(requestId)
            .filter(r -> r.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("GainRuleRequest", requestId));

        if (!"pending".equals(req.getStatus())) {
            throw new BadRequestException(
                "Demande déjà traitée (status=" + req.getStatus() + ")");
        }

        req.setStatus("rejected");
        req.setRejectionReason(dto.rejectionReason());
        req.setReviewedById(SecurityHelper.currentUserId());
        req.setReviewedAt(Instant.now());

        return repository.save(req).toDto();
    }
}
