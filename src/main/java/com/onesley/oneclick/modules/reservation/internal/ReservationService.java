package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.ReservationCreatedEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
        // Native query joints (anti N+1) — voir ReservationRepository.findAllWithJoins.
        // Tri figé par reservation_at DESC : aligné avec le tri historique
        // (Specification précédente) et avec l'index idx_reservations_at.
        return repository.findAllWithJoins(
                clientId, restaurantId, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "reservation_at")))
            .map(ReservationService::toDto);
    }

    /**
     * Conversion projection joints → DTO public. Préserve l'ordre des champs
     * documenté dans {@link ReservationDto}.
     */
    private static ReservationDto toDto(ReservationWithJoinsView v) {
        return new ReservationDto(
            v.getId(),
            v.getTenantId(),
            v.getClientId(),
            v.getRestaurantId(),
            v.getTableId(),
            v.getServiceId(),
            v.getReservationAt(),
            v.getGuestCount(),
            v.getStatus(),
            v.getNotes(),
            v.getCreatedAt(),
            v.getClientFirstName(),
            v.getClientLastName(),
            v.getRestaurantName(),
            v.getRestaurantCity(),
            v.getRestaurantImage(),
            v.getMealServiceName(),
            v.getZoneName(),
            v.getTableNumber(),
            v.getRefusalReason(),
            v.getCancellationReason()
        );
    }

    public ReservationDto findById(UUID id) {
        Reservation r = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Reservation", id));
        requireReservationAccess(r);
        return r.toDto();
    }

    /**
     * Lookup batch : retourne uniquement les résas accessibles à l'appelant.
     * Élimine le N+1 frontend ({@code useReservationGuests} + {@code useReservations})
     * qui appelait {@link #findById} pour chaque invitation. Les rows non
     * accessibles sont silencieusement omises (pas de 403 partiel).
     */
    public List<ReservationDto> findAccessibleByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return repository.findAllById(ids).stream()
            .filter(r -> r.getDeletedAt() == null)
            .filter(this::canAccess)
            .map(Reservation::toDto)
            .toList();
    }

    /** Variante non-throwing de {@link #requireReservationAccess(Reservation)} — pour batch. */
    private boolean canAccess(Reservation r) {
        UUID current = SecurityHelper.currentUserId();
        if (current == null) return false;
        if (current.equals(r.getClientId())) return true;
        if (SecurityHelper.isAdmin()) return true;
        Number staffCount = (Number) entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM restaurant_staffs
             WHERE user_id = :uid AND restaurant_id = :rid AND deleted_at IS NULL
            """)
            .setParameter("uid", current)
            .setParameter("rid", r.getRestaurantId())
            .getSingleResult();
        if (staffCount.intValue() > 0) return true;
        Number guestCount = (Number) entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM reservation_guests
             WHERE reservation_id = :resaId AND guest_user_id = :uid
            """)
            .setParameter("resaId", r.getId())
            .setParameter("uid", current)
            .getSingleResult();
        return guestCount.intValue() > 0;
    }

    /**
     * Politique d'accès à une réservation — 4 rôles légitimes :
     * <ul>
     *   <li>Le <b>client</b> de la résa (l'a créée)</li>
     *   <li>Un <b>admin</b> (SUPERADMIN / GROUP_ADMIN)</li>
     *   <li>Le <b>staff actif</b> du restaurant où la résa a lieu (workflow ProDesk)</li>
     *   <li>Un <b>guest invité</b> à la résa (workflow invitations Pocket)</li>
     * </ul>
     * <p>Variante throwing utilisée par {@link #findById} (renvoie 403 si interdit).
     * Pour la variante non-throwing utilisée par le batch, voir {@link #canAccess}.
     */
    private void requireReservationAccess(Reservation r) {
        if (SecurityHelper.currentUserId() == null) {
            throw new com.onesley.oneclick.exception.ForbiddenException("Authentification requise");
        }
        if (!canAccess(r)) {
            throw new com.onesley.oneclick.exception.ForbiddenException(
                "Accès interdit : vous n'êtes ni le client, ni un guest, ni staff de ce restaurant"
            );
        }
    }

    @Transactional
    public ReservationDto create(ReservationCreateDto dto) {
        if (dto.reservationAt().isBefore(Instant.now())) {
            throw new BadRequestException("reservationAt doit être dans le futur");
        }
        Tenant tenantRef = entityManager.getReference(Tenant.class, dto.tenantId());
        User clientRef = entityManager.getReference(User.class, dto.clientId());
        Reservation r = new Reservation(
            UUID.randomUUID(), tenantRef, clientRef, dto.restaurantId(),
            dto.reservationAt(), dto.guestCount()
        );
        if (dto.tableId() != null) {
            r.setTableId(dto.tableId());
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
