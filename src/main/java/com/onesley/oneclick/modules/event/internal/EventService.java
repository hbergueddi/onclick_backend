package com.onesley.oneclick.modules.event.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
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
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepo;
    private final EventParticipationRepository participationRepo;

    @PersistenceContext
    private EntityManager entityManager;

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
            e.setRestaurantId(dto.restaurantId());
        }
        if (dto.description() != null) e.setDescription(dto.description());
        if (dto.eventType() != null)   e.setEventType(dto.eventType());
        if (dto.capacity() != null)    e.setCapacity(dto.capacity());
        // V20 — Sprint D Elite fields
        if (dto.eventEnd() != null)    e.setEventEnd(dto.eventEnd());
        if (dto.minTier() != null)     e.setMinTier(dto.minTier());
        if (dto.imageUrl() != null)    e.setImageUrl(dto.imageUrl());
        if (dto.locationName() != null) e.setLocationName(dto.locationName());
        if (dto.isActive() != null)    e.setActive(dto.isActive());
        return eventRepo.save(e).toDto();
    }

    /**
     * Sprint D — Patch partiel d'un event (admin Elite).
     */
    @Transactional
    public EventDto patch(UUID id, com.onesley.oneclick.modules.event.api.EventDtos.EventPatchDto dto) {
        Event e = eventRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Event", id));
        if (dto.title() != null)        e.setTitle(dto.title());
        if (dto.description() != null)  e.setDescription(dto.description());
        if (dto.eventType() != null)    e.setEventType(dto.eventType());
        if (dto.eventAt() != null)      e.setEventAt(dto.eventAt());
        if (dto.eventEnd() != null)     e.setEventEnd(dto.eventEnd());
        if (dto.capacity() != null)     e.setCapacity(dto.capacity());
        if (dto.minTier() != null)      e.setMinTier(dto.minTier());
        if (dto.imageUrl() != null)     e.setImageUrl(dto.imageUrl());
        if (dto.locationName() != null) e.setLocationName(dto.locationName());
        if (dto.isActive() != null)     e.setActive(dto.isActive());
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

    /** Sprint D — RSVPs d'un user (Pocket "Mes événements"). */
    public List<ParticipationDto> findParticipationsByUser(UUID userId) {
        return participationRepo.findAllByUserId(userId).stream()
            .map(EventParticipation::toDto)
            .toList();
    }

    @Transactional
    public ParticipationDto rsvp(ParticipationCreateDto dto) {
        // Sprint D — Vérifs Elite : capacity + duplicates
        Event event = eventRepo.findById(dto.eventId())
            .filter(e -> e.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Event", dto.eventId()));

        // Anti-doublon : 1 RSVP par user par event (UNIQUE constraint DB)
        participationRepo.findByEventIdAndUserId(dto.eventId(), dto.userId())
            .ifPresent(p -> {
                throw new ConflictException("User déjà RSVP sur cet event");
            });

        String status = dto.status() != null ? dto.status() : "going";

        // Si going + capacity : vérif places dispo + increment compteur
        if ("going".equals(status) && !event.hasCapacity()) {
            throw new BadRequestException("Event complet (places_taken >= capacity)");
        }

        Event eventRef = entityManager.getReference(Event.class, dto.eventId());
        User userRef = entityManager.getReference(User.class, dto.userId());
        EventParticipation p = new EventParticipation(UUID.randomUUID(), eventRef, userRef, status);
        if (dto.plusOneName() != null) p.setPlusOneName(dto.plusOneName());

        EventParticipation saved = participationRepo.save(p);

        // Atomique : increment places_taken (denormalisé pour fast read)
        if ("going".equals(status)) {
            event.incrementPlacesTaken();
            eventRepo.save(event);
        }

        return saved.toDto();
    }

    /**
     * Sprint D — Cancel RSVP user's own event (Pocket EliteClub).
     * Decrement places_taken si status était going.
     */
    @Transactional
    public void cancelRsvp(UUID eventId, UUID userId) {
        EventParticipation p = participationRepo.findByEventIdAndUserId(eventId, userId)
            .orElseThrow(() -> new NotFoundException("EventParticipation for user " + userId, eventId));

        boolean wasGoing = "going".equals(p.getStatus());
        participationRepo.delete(p);

        // Décrémenter places_taken si user était going
        if (wasGoing) {
            Event event = eventRepo.findById(eventId).orElse(null);
            if (event != null) {
                event.decrementPlacesTaken();
                eventRepo.save(event);
            }
        }
    }

    // ─── Elite-specific (Sprint D) ──────────────────────────────────────────

    /** Events Elite actifs futurs (Pocket EliteClub). */
    public List<EventDto> findEliteActiveUpcoming() {
        return eventRepo.findEliteActiveUpcoming().stream()
            .map(Event::toDto)
            .toList();
    }

    /** Tous les events Elite admin (dashboard Forge). */
    public List<EventDto> findAllElite() {
        return eventRepo.findAllElite().stream()
            .map(Event::toDto)
            .toList();
    }
}
