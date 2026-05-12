package com.onesley.oneclick.modules.promotion.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.shared.events.OfferCreatedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import com.onesley.oneclick.modules.promotion.api.OfferCreateDto;
import com.onesley.oneclick.modules.promotion.api.OfferDto;
import com.onesley.oneclick.modules.promotion.api.OfferPatchDto;

@Service
@Transactional(readOnly = true)
public class OfferService {

    /** Valeurs autorisées pour Offer.type (miroir du CHECK V15 côté DB). */
    private static final Set<String> ALLOWED_TYPES = Set.of("promo", "bonus", "reco");

    private final OfferRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    public OfferService(OfferRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public Page<OfferDto> findAll(UUID restaurantId, Boolean activeOnly, int page, int size) {
        Specification<Offer> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (restaurantId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("restaurantId"), restaurantId));
        if (Boolean.TRUE.equals(activeOnly)) {
            Instant now = Instant.now();
            spec = spec
                .and((root, q, cb) -> cb.isTrue(root.get("enabled")))
                .and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("startsAt"), now))
                .and((root, q, cb) -> cb.greaterThan(root.get("expiresAt"), now));
        }
        return repository.findAll(spec, PageRequest.of(page, size, Sort.by("startsAt").descending()))
            .map(Offer::toDto);
    }

    public OfferDto findById(UUID id) {
        return repository.findById(id)
            .filter(o -> o.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Offer", id))
            .toDto();
    }

    @Transactional
    public OfferDto create(OfferCreateDto dto) {
        if (dto.expiresAt().isBefore(dto.startsAt())) {
            throw new BadRequestException("expiresAt doit être > startsAt");
        }
        String type = dto.type() != null ? dto.type() : "promo";
        validateType(type);
        validatePts(type, dto.pts());

        Offer o = new Offer(UUID.randomUUID(), dto.restaurantId(), dto.title(), dto.startsAt(), dto.expiresAt());
        o.setDescription(dto.description());
        o.setDiscountPct(dto.discountPct());
        o.setDiscountAmount(dto.discountAmount());
        o.setType(type);
        o.setPts(dto.pts());
        Offer saved = repository.save(o);

        // Publish event for downstream consumers (notification campaign trigger, etc.)
        // tenant_id auto-rempli par trigger DB V10 — on lookup pour l'event payload
        UUID tenantId = (UUID) entityManager.createNativeQuery(
            "SELECT tenant_id FROM restaurants WHERE id = ?")
            .setParameter(1, dto.restaurantId())
            .getSingleResult();
        eventPublisher.publishEvent(new OfferCreatedEvent(
            saved.getId(), dto.restaurantId(), tenantId,
            dto.title(), dto.description(),
            dto.startsAt(), dto.expiresAt(),
            dto.discountPct(), dto.discountAmount()
        ));

        return saved.toDto();
    }

    /**
     * Mise à jour partielle (PATCH) — applique uniquement les champs non null
     * du DTO. Re-valide le couple {@code startsAt} / {@code expiresAt} après
     * application, et le couple {@code type} / {@code pts}.
     */
    @Transactional
    public OfferDto patch(UUID id, OfferPatchDto dto) {
        Offer o = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Offer", id));

        if (dto.title() != null) o.setTitle(dto.title());
        if (dto.description() != null) o.setDescription(dto.description());
        if (dto.startsAt() != null) o.setStartsAt(dto.startsAt());
        if (dto.expiresAt() != null) o.setExpiresAt(dto.expiresAt());
        if (dto.discountPct() != null) o.setDiscountPct(dto.discountPct());
        if (dto.discountAmount() != null) o.setDiscountAmount(dto.discountAmount());
        if (dto.enabled() != null) o.setEnabled(dto.enabled());
        if (dto.type() != null) {
            validateType(dto.type());
            o.setType(dto.type());
        }
        if (dto.pts() != null) o.setPts(dto.pts());

        // Invariants finaux (post-merge)
        if (o.getExpiresAt().isBefore(o.getStartsAt())) {
            throw new BadRequestException("expiresAt doit être > startsAt");
        }
        validatePts(o.getType(), o.getPts());

        return repository.save(o).toDto();
    }

    @Transactional
    public void softDelete(UUID id) {
        Offer o = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Offer", id));
        o.markDeleted();
        repository.save(o);
    }

    private static void validateType(String type) {
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BadRequestException(
                "type doit être ∈ " + ALLOWED_TYPES + " (reçu: " + type + ")");
        }
    }

    private static void validatePts(String type, Integer pts) {
        if (pts != null && pts <= 0) {
            throw new BadRequestException("pts doit être strictement positif");
        }
        // pts attendu uniquement pour type=bonus ; on logge sans bloquer si renseigné
        // sur une promo classique (use case: bonus combiné à une réduction futur).
    }
}
