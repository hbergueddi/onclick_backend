package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.core.identity.internal.User;
import com.onesley.oneclick.core.tenant.internal.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.modules.restaurant.internal.Restaurant;
import com.onesley.oneclick.modules.restaurant.internal.MealService;
import com.onesley.oneclick.modules.restaurant.internal.RestaurantTable;
import com.onesley.oneclick.shared.events.ReservationCreatedEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
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
import com.onesley.oneclick.modules.reservation.api.ReservationCreateDto;
import com.onesley.oneclick.modules.reservation.api.ReservationDto;

/**
 * Service réservation — création + workflow transitions.
 */
@Service
@Transactional(readOnly = true)
public class ReservationService {

    private static final Set<String> VALID_STATUSES = Set.of(
        "pending", "confirmed", "refused", "counter_proposed", "cancelled", "honored", "no_show"
    );

    private final ReservationRepository repository;
    private final ReservationStatusHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    public ReservationService(ReservationRepository repository,
                              ReservationStatusHistoryRepository historyRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
    }

    public Page<ReservationDto> findAll(UUID clientId, UUID restaurantId, String status,
                                        int page, int size) {
        Specification<Reservation> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (clientId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("clientId"), clientId));
        if (restaurantId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("restaurantId"), restaurantId));
        if (status != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        return repository.findAll(spec, PageRequest.of(page, size, Sort.by("reservationAt").descending()))
            .map(Reservation::toDto);
    }

    public ReservationDto findById(UUID id) {
        Reservation r = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Reservation", id));
        SecurityHelper.requireOwnerOrAdmin(r.getClientId());
        return r.toDto();
    }

    @Transactional
    public ReservationDto create(ReservationCreateDto dto) {
        if (dto.reservationAt().isBefore(Instant.now())) {
            throw new BadRequestException("reservationAt doit être dans le futur");
        }
        Tenant tenantRef = entityManager.getReference(Tenant.class, dto.tenantId());
        User clientRef = entityManager.getReference(User.class, dto.clientId());
        Restaurant restoRef = entityManager.getReference(Restaurant.class, dto.restaurantId());
        Reservation r = new Reservation(
            UUID.randomUUID(), tenantRef, clientRef, restoRef,
            dto.reservationAt(), dto.guestCount()
        );
        if (dto.tableId() != null) {
            r.setTable(entityManager.getReference(RestaurantTable.class, dto.tableId()));
        }
        if (dto.notes() != null) r.setNotes(dto.notes());
        Reservation saved = repository.save(r);

        // Audit workflow — état initial pending
        ReservationStatusHistory hist = new ReservationStatusHistory(
            UUID.randomUUID(), saved, null, "pending", clientRef
        );
        historyRepository.save(hist);

        // ─── Publish event Spring Modulith (Phase 2 spec §21) ────────────
        // Listeners @ApplicationModuleListener consomment cet event en async
        // après commit. L'event est aussi externalisé vers Kafka pour
        // consommation cross-service (notification-service, etc.)
        //
        // NB : on prend les UUID des DTOs (jamais NULL) plutôt que de
        // saved.getClientId() (NULL côté Hibernate car FK column avec
        // insertable=false). Pattern récurrent dans le codebase.
        eventPublisher.publishEvent(new ReservationCreatedEvent(
            saved.getId(),
            dto.clientId(),
            dto.restaurantId(),
            dto.tenantId(),
            dto.reservationAt(),
            dto.guestCount(),
            saved.getStatus()
        ));

        return saved.toDto();
    }

    @Transactional
    public ReservationDto changeStatus(UUID id, String newStatus, UUID changedById, String reason) {
        if (!VALID_STATUSES.contains(newStatus)) {
            throw new BadRequestException("Status invalide : " + newStatus);
        }
        Reservation r = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Reservation", id));
        String oldStatus = r.getStatus();
        if (oldStatus.equals(newStatus)) {
            return r.toDto(); // no-op
        }
        r.setStatus(newStatus);
        User actor = changedById != null ? entityManager.getReference(User.class, changedById) : null;
        ReservationStatusHistory hist = new ReservationStatusHistory(
            UUID.randomUUID(), r, oldStatus, newStatus, actor
        );
        hist.setReason(reason);
        historyRepository.save(hist);
        Reservation saved = repository.save(r);

        // Publish status change event for downstream consumers (notifications, etc.)
        eventPublisher.publishEvent(new ReservationStatusChangedEvent(
            saved.getId(),
            r.getClientId(),
            r.getRestaurantId(),
            r.getTenantId(),
            oldStatus, newStatus, reason,
            java.time.Instant.now()
        ));

        return saved.toDto();
    }
}
