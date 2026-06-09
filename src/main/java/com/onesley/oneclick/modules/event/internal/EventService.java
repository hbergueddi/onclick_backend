package com.onesley.oneclick.modules.event.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.security.TenantScope;
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
import java.util.Set;
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
    private final TenantScope tenantScope;

    @PersistenceContext
    private EntityManager entityManager;

    // ─── Events ──────────────────────────────────────────────────────────────

    public Page<EventDto> findAll(UUID tenantId, UUID restaurantId, Boolean upcomingOnly, int page, int size) {
        Specification<Event> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (tenantId != null) {
            // Périmètre tenant (fuite de périmètre) : le param tenantId fourni par le client doit être
            // dans son périmètre visible ({tenant public} ∪ memberships) ; sinon 403 (ne pas exposer un
            // programme — PCC/HOMU — non accessible). SUPERADMIN (canSeeTenant true) → autorisé.
            if (!tenantScope.canSeeTenant(tenantId)) {
                throw new ForbiddenException("Tenant hors périmètre");
            }
            spec = spec.and((root, q, cb) -> cb.equal(root.get("tenantId"), tenantId));
        } else {
            // Pas de tenantId : scoper par défaut au périmètre visible. SUPERADMIN (null) → aucun filtre.
            Set<UUID> visible = tenantScope.visibleTenantIdsOrNull();
            if (visible != null) {
                spec = spec.and((root, q, cb) -> root.get("tenantId").in(visible));
            }
        }
        if (restaurantId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("restaurantId"), restaurantId));
        if (Boolean.TRUE.equals(upcomingOnly)) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("eventAt"), Instant.now()));
        }
        return eventRepo.findAll(spec, PageRequest.of(page, size, Sort.by("eventAt").ascending()))
            .map(Event::toDto);
    }

    public EventDto findById(UUID id) {
        Event e = eventRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Event", id));
        // Hors périmètre : 404 (ne pas divulguer l'existence d'un event d'un programme non accessible).
        if (!tenantScope.canSeeTenant(e.getTenantId())) {
            throw new NotFoundException("Event", id);
        }
        return e.toDto();
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
        // Périmètre tenant : la liste des inscrits (PII membres) d'un event d'un programme hors
        // périmètre ne doit pas fuiter → 404 si l'event n'est pas visible (cohérent avec findById).
        Event event = eventRepo.findById(eventId)
            .filter(e -> e.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Event", eventId));
        if (!tenantScope.canSeeTenant(event.getTenantId())) {
            throw new NotFoundException("Event", eventId);
        }
        return participationRepo.findAllByEventIdFetchUser(eventId).stream()
            .map(EventParticipation::toDto)
            .toList();
    }

    /** Sprint D — RSVPs d'un user (Pocket "Mes événements"). */
    public List<ParticipationDto> findParticipationsByUser(UUID userId) {
        return participationRepo.findAllByUserIdFetchUser(userId).stream()
            .map(EventParticipation::toDto)
            .toList();
    }

    @Transactional
    public ParticipationDto rsvp(ParticipationCreateDto dto) {
        // ABAC self-scope : un membre ne RSVP que pour LUI-MÊME (anti-spoof) ;
        // staff/admin peuvent RSVP pour autrui (ex. inscription au guichet).
        final UUID targetUserId =
            SecurityHelper.isStaffOrAdmin() ? dto.userId() : SecurityHelper.currentUserId();

        // Sprint D — Vérifs Elite : capacity + duplicates
        Event event = eventRepo.findById(dto.eventId())
            .filter(e -> e.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Event", dto.eventId()));

        // Anti-doublon : 1 RSVP par user par event (UNIQUE constraint DB)
        participationRepo.findByEventIdAndUserId(dto.eventId(), targetUserId)
            .ifPresent(p -> {
                throw new ConflictException("User déjà RSVP sur cet event");
            });

        String status = dto.status() != null ? dto.status() : "going";

        // Si going + capacity : vérif places dispo + increment compteur
        if ("going".equals(status) && !event.hasCapacity()) {
            throw new BadRequestException("Event complet (places_taken >= capacity)");
        }

        Event eventRef = entityManager.getReference(Event.class, dto.eventId());
        User userRef = entityManager.getReference(User.class, targetUserId);
        EventParticipation p = new EventParticipation(UUID.randomUUID(), eventRef, userRef, status);
        if (dto.plusOneName() != null) p.setPlusOneName(dto.plusOneName());

        EventParticipation saved = participationRepo.save(p);
        // user_id/event_id sont insertable=false (colonnes portées par les associations) :
        // flush + refresh pour que le DTO de réponse porte le userId/eventId persistés
        // (sinon null dans la réponse POST — cohérent avec un GET ultérieur).
        entityManager.flush();
        entityManager.refresh(saved);

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
        // ABAC self-scope : un membre n'annule que SON propre RSVP ; staff/admin pour autrui.
        if (!SecurityHelper.isStaffOrAdmin() && !userId.equals(SecurityHelper.currentUserId())) {
            throw new ForbiddenException("Annulation RSVP limitée à son propre compte");
        }
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

    /** Events Elite actifs futurs (Pocket EliteClub). Scopé au périmètre tenant visible du caller. */
    public List<EventDto> findEliteActiveUpcoming() {
        Set<UUID> visible = tenantScope.visibleTenantIdsOrNull();
        return eventRepo.findEliteActiveUpcoming().stream()
            .filter(e -> visible == null || visible.contains(e.getTenantId()))
            .map(Event::toDto)
            .toList();
    }

    /** Tous les events Elite admin (dashboard Forge). Scopé au périmètre tenant visible du caller. */
    public List<EventDto> findAllElite() {
        Set<UUID> visible = tenantScope.visibleTenantIdsOrNull();
        return eventRepo.findAllElite().stream()
            .filter(e -> visible == null || visible.contains(e.getTenantId()))
            .map(Event::toDto)
            .toList();
    }
}
