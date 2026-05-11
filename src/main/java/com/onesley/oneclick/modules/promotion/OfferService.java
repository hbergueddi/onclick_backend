package com.onesley.oneclick.modules.promotion;

import com.onesley.oneclick.exception.BadRequestException;
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

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OfferService {

    private final OfferRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public OfferService(OfferRepository repository) {
        this.repository = repository;
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
            .map(OfferDto::from);
    }

    public OfferDto findById(UUID id) {
        return OfferDto.from(repository.findById(id)
            .filter(o -> o.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Offer", id)));
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
        return OfferDto.from(repository.save(o));
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
