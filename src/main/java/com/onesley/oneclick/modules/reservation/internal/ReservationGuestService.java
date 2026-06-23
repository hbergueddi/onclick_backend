package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.reservation.api.ReservationGuestDto;
import com.onesley.oneclick.shared.events.ReservationGuestAddedEvent;
import com.onesley.oneclick.shared.events.ReservationGuestRemovedEvent;
import com.onesley.oneclick.shared.events.ReservationGuestRespondedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Service du workflow {@link ReservationGuest} — invite, list, status update,
 * cancel, delete.
 *
 * <p>Pattern : {@code @Transactional(readOnly=true)} par défaut, écritures
 * explicites. Service séparé de {@link ReservationService} pour éviter de
 * mélanger la responsabilité workflow réservation principale et workflow
 * invitation guests.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReservationGuestService {

    private static final Set<String> VALID_STATUSES = Set.of(
        "linked", "invited", "accepted", "refused", "cancelled"
    );

    private final ReservationGuestRepository repository;
    private final ReservationRepository reservationRepository;
    private final ApplicationEventPublisher eventPublisher; // notif server-side (ReservationGuestAddedEvent)

    @PersistenceContext
    private EntityManager entityManager;

    /** Tous les guests d'une réservation. */
    public List<ReservationGuestDto> findByReservation(UUID reservationId) {
        return repository.findAllByReservationId(reservationId)
            .stream().map(ReservationGuest::toDto).toList();
    }

    /** Toutes les invitations reçues par un user (Pocket → "Mes invitations"). */
    public List<ReservationGuestDto> findByGuestUser(UUID guestUserId) {
        return repository.findAllByGuestUserId(guestUserId)
            .stream().map(ReservationGuest::toDto).toList();
    }

    /** Toutes les invitations ENVOYÉES par un organisateur (Pocket → "Invitations envoyées"). */
    public List<ReservationGuestDto> findByInviter(UUID inviterId) {
        return repository.findAllByInvitedById(inviterId)
            .stream().map(ReservationGuest::toDto).toList();
    }

    /**
     * Invite un guest à une réservation. Au moins un identifiant requis
     * ({@code guestUserId} OU {@code guestPhone} OU {@code guestName}).
     *
     * <p>Si {@code guestUserId} fourni et déjà invité → {@link ConflictException}.
     * Si {@code guestPhone} fourni et déjà invité → idempotent (re-active si
     * cancelled/refused via UPDATE status='invited').
     */
    @Transactional
    public ReservationGuestDto invite(UUID reservationId, ReservationGuestDto.CreateDto dto) {
        if (dto.guestUserId() == null && dto.guestPhone() == null && dto.guestName() == null) {
            throw new BadRequestException(
                "Au moins un identifiant requis : guestUserId, guestPhone, ou guestName");
        }

        // Vérif réservation existe
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new NotFoundException("Reservation", reservationId));

        // Idempotence : si guestUserId déjà attaché, conflict (unique index DB)
        if (dto.guestUserId() != null) {
            boolean alreadyInvited = repository.findAllByReservationId(reservationId).stream()
                .anyMatch(g -> dto.guestUserId().equals(g.getGuestUserId()));
            if (alreadyInvited) {
                throw new ConflictException(
                    "Guest user déjà invité à cette réservation : " + dto.guestUserId());
            }
        }

        // Construit l'entité (référence lazy via getReference pour éviter SELECT)
        User guestUser = dto.guestUserId() != null
            ? entityManager.getReference(User.class, dto.guestUserId())
            : null;
        User inviter = dto.invitedBy() != null
            ? entityManager.getReference(User.class, dto.invitedBy())
            : null;
        String status = dto.status() != null ? dto.status() : "linked";

        ReservationGuest guest = new ReservationGuest(
            UUID.randomUUID(),
            reservation,
            guestUser,
            dto.guestName(),
            dto.guestPhone(),
            inviter,
            status
        );
        ReservationGuestDto result = repository.save(guest).toDto();
        // Notif server-side à l'invité IDENTIFIÉ (le CLIENT organisateur n'a pas CREATE:NOTIFICATIONS).
        // Guests anonymes (téléphone/nom seul) non notifiés (pas de compte destinataire).
        if (dto.guestUserId() != null) {
            eventPublisher.publishEvent(
                new ReservationGuestAddedEvent(reservationId, dto.guestUserId(), dto.invitedBy()));
        }
        return result;
    }

    /**
     * Change le statut d'une invitation (typiquement par le guest pour répondre
     * "accepté" / "refusé", ou par l'organisateur pour "cancelled").
     *
     * <p>Réinitialise {@code seenByHost=false} pour notifier l'organisateur
     * qu'il y a une nouvelle réponse à voir.
     */
    @Transactional
    public ReservationGuestDto updateStatus(UUID guestId, ReservationGuestDto.StatusUpdateDto dto) {
        if (!VALID_STATUSES.contains(dto.status())) {
            throw new BadRequestException(
                "Statut invalide : " + dto.status() + " (autorisés : " + VALID_STATUSES + ")");
        }
        ReservationGuest guest = repository.findById(guestId)
            .orElseThrow(() -> new NotFoundException("ReservationGuest", guestId));
        guest.setStatus(dto.status());
        guest.setSeenByHost(false); // notifier organisateur de la nouvelle réponse
        ReservationGuestDto result = repository.save(guest).toDto();
        // Notif server-side à l'organisateur quand l'invité RÉPOND (accepte/décline) —
        // l'invité CLIENT n'a pas CREATE:NOTIFICATIONS. (Le badge seenByHost reste le signal UI.)
        if ("accepted".equals(dto.status()) || "refused".equals(dto.status())) {
            UUID organizerId = reservationRepository.findById(guest.getReservationId())
                .map(Reservation::getClientId).orElse(null);
            if (organizerId != null) {
                eventPublisher.publishEvent(new ReservationGuestRespondedEvent(
                    guest.getReservationId(), organizerId, "accepted".equals(dto.status())));
            }
        }
        return result;
    }

    /** Marque les réponses comme vues par l'organisateur (badge UI). */
    @Transactional
    public void markSeen(UUID reservationId) {
        repository.findAllByReservationId(reservationId).forEach(g -> {
            if (!g.isSeenByHost()) {
                g.setSeenByHost(true);
            }
        });
    }

    /** Supprime un guest (organisateur uniquement). */
    @Transactional
    public void delete(UUID guestId) {
        ReservationGuest guest = repository.findById(guestId)
            .orElseThrow(() -> new NotFoundException("ReservationGuest", guestId));
        // Capture les ids AVANT la suppression (l'entité devient détachée après delete).
        UUID invitedUserId = guest.getGuestUserId();
        UUID reservationId = guest.getReservationId();
        repository.delete(guest);
        // Lot B14 — notif server-side à l'invité IDENTIFIÉ que son invitation a été annulée
        // (le CLIENT organisateur n'a pas CREATE:NOTIFICATIONS). Guests anonymes (téléphone/nom
        // seul, sans guestUserId) non notifiés (pas de compte destinataire). Calque l'invite().
        // organizerName non résolu ici (pas de dépendance identity dans ce service) → null →
        // le handler affiche un corps générique.
        if (invitedUserId != null) {
            eventPublisher.publishEvent(
                new ReservationGuestRemovedEvent(reservationId, invitedUserId, null));
        }
    }
}
