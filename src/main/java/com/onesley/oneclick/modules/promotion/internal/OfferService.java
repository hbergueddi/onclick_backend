package com.onesley.oneclick.modules.promotion.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.internal.Restaurant;
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
import java.util.UUID;
import com.onesley.oneclick.modules.promotion.api.OfferCreateDto;
import com.onesley.oneclick.modules.promotion.api.OfferDto;

@Service
@Transactional(readOnly = true)
public class OfferService {

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
        Restaurant restoRef = entityManager.getReference(Restaurant.class, dto.restaurantId());
        Offer o = new Offer(UUID.randomUUID(), restoRef, dto.title(), dto.startsAt(), dto.expiresAt());
        o.setDescription(dto.description());
        o.setDiscountPct(dto.discountPct());
        o.setDiscountAmount(dto.discountAmount());
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

    @Transactional
    public void softDelete(UUID id) {
        Offer o = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Offer", id));
        o.markDeleted();
        repository.save(o);
    }
}
