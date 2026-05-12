package com.onesley.oneclick.modules.event.internal;

import com.onesley.oneclick.core.identity.internal.User;
import com.onesley.oneclick.core.tenant.internal.Tenant;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.internal.Restaurant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.event.api.EventDtos.*;
import com.onesley.oneclick.modules.event.api.Event;
import com.onesley.oneclick.modules.event.api.EventDtos;
import com.onesley.oneclick.modules.event.api.EventDtos.EventCreateDto;
import com.onesley.oneclick.modules.event.api.EventDtos.EventDto;
import com.onesley.oneclick.modules.event.api.EventDtos.ParticipationCreateDto;
import com.onesley.oneclick.modules.event.api.EventDtos.ParticipationDto;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepo;
    private final EventParticipationRepository participationRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public EventService(EventRepository eventRepo, EventParticipationRepository participationRepo) {
        this.eventRepo = eventRepo;
        this.participationRepo = participationRepo;
    }

    // ─── Events ──────────────────────────────────────────────────────────────

    public Page<EventDto> findAll(UUID tenantId, UUID restaurantId, Boolean upcomingOnly, int page, int size) {
        Specification<Event> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (tenantId != null)     spec = spec.and((root, q, cb) -> cb.equal(root.get("tenantId"), tenantId));
        if (restaurantId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("restaurantId"), restaurantId));
        if (Boolean.TRUE.equals(upcomingOnly)) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("eventAt"), Instant.now()));
        }
        return eventRepo.findAll(spec, PageRequest.of(page, size, Sort.by("eventAt").ascending()))
            .map(Event::toDto);
    }

    public EventDto findById(UUID id) {
        return eventRepo.findById(id)
            .filter(e -> e.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Event", id))
            .toDto();
    }

    @Transactional
    public EventDto create(EventCreateDto dto) {
        Tenant tenantRef = entityManager.getReference(Tenant.class, dto.tenantId());
        Event e = new Event(UUID.randomUUID(), tenantRef, dto.title(), dto.eventAt());
        if (dto.restaurantId() != null) {
            e.setRestaurant(entityManager.getReference(Restaurant.class, dto.restaurantId()));
        }
        if (dto.description() != null) e.setDescription(dto.description());
        if (dto.eventType() != null)   e.setEventType(dto.eventType());
        if (dto.capacity() != null)    e.setCapacity(dto.capacity());
        return eventRepo.save(e).toDto();
    }

    @Transactional
    public void softDelete(UUID id) {
        Event e = eventRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Event", id));
        e.markDeleted();
        eventRepo.save(e);
    }

    // ─── Participations (RSVP) ───────────────────────────────────────────────

    public List<ParticipationDto> findParticipations(UUID eventId) {
        return participationRepo.findAllByEventId(eventId).stream()
            .map(EventParticipation::toDto)
            .toList();
    }

    @Transactional
    public ParticipationDto rsvp(ParticipationCreateDto dto) {
        Event eventRef = entityManager.getReference(Event.class, dto.eventId());
        User userRef = entityManager.getReference(User.class, dto.userId());
        String status = dto.status() != null ? dto.status() : "going";
        EventParticipation p = new EventParticipation(UUID.randomUUID(), eventRef, userRef, status);
        return participationRepo.save(p).toDto();
    }
}
