package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.reservation.api.NoShowDisputeDtos.CreateDisputeDto;
import com.onesley.oneclick.modules.reservation.api.NoShowDisputeDtos.NoShowDisputeDto;
import com.onesley.oneclick.modules.reservation.api.NoShowDisputeDtos.ResolveDisputeDto;
import com.onesley.oneclick.security.RestaurantAccessGuard;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.NoShowDisputeResolvedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service du domaine contestation no-show (Feature #3).
 *
 * <h3>Phases d'escalade</h3>
 * <p>Calculées depuis {@code reservation.no_show_marked_at} :
 * <ul>
 *   <li>{@code resto}    — 0-1h (le restaurant tranche en premier)</li>
 *   <li>{@code support}  — 1-48h (le support OneClick prend le relais)</li>
 *   <li>{@code expired}  — 48h+ (plus contestable)</li>
 * </ul>
 * La fenêtre est testable : l'horloge est injectée ({@link Clock}).
 *
 * <h3>RBAC / ABAC</h3>
 * <p>Le RBAC grossier ({@code hasAuthority('VERB:DISPUTES')}) est au contrôleur ;
 * ici on applique l'ABAC fin (ownership client, staff-of-restaurant, phase). Aucune
 * dépendance {@code reservation → loyalty} : la résolution {@code accepted} publie un
 * event consommé par loyalty (reversal de la pénalité).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NoShowDisputeService {

    public static final String PHASE_RESTO = "resto";
    public static final String PHASE_SUPPORT = "support";
    public static final String PHASE_EXPIRED = "expired";

    private static final Duration RESTO_WINDOW = Duration.ofHours(1);
    private static final Duration SUPPORT_WINDOW = Duration.ofHours(48);

    private final NoShowDisputeRepository disputeRepository;
    private final ReservationRepository reservationRepository;
    private final RestaurantAccessGuard restaurantAccessGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    // ═══════════════════════════════════════════════════════════════════════
    //  Calcul de phase (testable via Clock)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Phase d'escalade courante depuis l'horodatage du no_show.
     * {@code null} markedAt → {@code expired} (on ne peut pas contester ce qu'on ne sait pas dater).
     */
    public String computePhase(Instant noShowMarkedAt) {
        if (noShowMarkedAt == null) return PHASE_EXPIRED;
        Duration elapsed = Duration.between(noShowMarkedAt, Instant.now(clock));
        if (elapsed.isNegative() || elapsed.compareTo(RESTO_WINDOW) < 0) return PHASE_RESTO;
        if (elapsed.compareTo(SUPPORT_WINDOW) < 0) return PHASE_SUPPORT;
        return PHASE_EXPIRED;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Création (client)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Le client conteste un no_show. Éligibilité :
     * <ul>
     *   <li>la résa lui appartient (clientId == current user),</li>
     *   <li>{@code status == 'no_show'},</li>
     *   <li>{@code late_cancellation == false} (annulation tardive = non contestable),</li>
     *   <li>phase != expired,</li>
     *   <li>aucune dispute existante OU la dernière est {@code refused} ET la
     *       re-contestation fournit une {@code photoUrl} (preuve obligatoire).</li>
     * </ul>
     */
    @Transactional
    public NoShowDisputeDto create(UUID reservationId, CreateDisputeDto dto) {
        Reservation r = reservationRepository.findById(reservationId)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Reservation", reservationId));

        // ABAC : seul le client de la résa conteste son propre no_show.
        UUID current = SecurityHelper.currentUserId();
        if (current == null) {
            throw new ForbiddenException("Authentification requise");
        }
        if (!current.equals(r.getClientId())) {
            throw new ForbiddenException(
                "Accès interdit : seule la cliente / le client de la réservation peut contester ce no-show");
        }

        if (!"no_show".equals(r.getStatus())) {
            throw new BadRequestException(
                "Contestation impossible : la réservation n'est pas en statut no_show");
        }
        if (r.isLateCancellation()) {
            throw new BadRequestException(
                "Contestation impossible : no-show suite à une annulation tardive (non contestable)");
        }

        String phase = computePhase(r.getNoShowMarkedAt());
        if (PHASE_EXPIRED.equals(phase)) {
            throw new BadRequestException(
                "Contestation impossible : le délai de contestation (48h) est dépassé");
        }

        // Re-contestation : autorisée seulement après un refus, avec photo obligatoire.
        List<NoShowDispute> existing = disputeRepository.findByReservationIdOrderByCreatedAtDesc(reservationId);
        if (!existing.isEmpty()) {
            NoShowDispute last = existing.get(0);
            if ("pending".equals(last.getStatus())) {
                throw new ConflictException(
                    "Une contestation est déjà en cours pour cette réservation");
            }
            if ("accepted".equals(last.getStatus())) {
                throw new ConflictException(
                    "Cette réservation a déjà une contestation acceptée");
            }
            // last == refused → re-contestation autorisée AVEC preuve photo.
            if (dto.photoUrl() == null || dto.photoUrl().isBlank()) {
                throw new BadRequestException(
                    "Re-contestation après refus : une photo justificative (photoUrl) est obligatoire");
            }
        }

        NoShowDispute dispute = new NoShowDispute(
            UUID.randomUUID(), reservationId, r.getClientId(), r.getRestaurantId(),
            phase, dto.reason(), dto.photoUrl());
        return disputeRepository.save(dispute).toDto();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Lectures (owner / staff / admin)
    // ═══════════════════════════════════════════════════════════════════════

    /** Contestations d'une réservation — accès client-owner / staff du resto / admin. */
    public List<NoShowDisputeDto> findByReservation(UUID reservationId) {
        Reservation r = reservationRepository.findById(reservationId)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Reservation", reservationId));
        requireReadAccess(r);
        return disputeRepository.findByReservationIdOrderByCreatedAtDesc(reservationId).stream()
            .map(NoShowDispute::toDto).toList();
    }

    /**
     * Dashboard resto/support/admin — filtres {@code status} / {@code phase} optionnels.
     * <p>ABAC : admin → toutes ; non-admin (restaurateur/staff) → uniquement les
     * contestations des restaurants dont il est staff actif (scope tenant).
     */
    public List<NoShowDisputeDto> findAll(String status, String phase) {
        List<NoShowDispute> rows;
        if (SecurityHelper.isAdmin()) {
            rows = disputeRepository.findAllByOrderByCreatedAtDesc();
        } else {
            UUID callerId = SecurityHelper.currentUserId();
            if (callerId == null) throw new ForbiddenException("Authentification requise");
            List<UUID> myRestaurants = activeStaffRestaurantIds(callerId);
            if (myRestaurants.isEmpty()) return List.of();
            rows = disputeRepository.findByRestaurantIdInOrderByCreatedAtDesc(myRestaurants);
        }
        return rows.stream()
            .filter(d -> status == null || status.isBlank() || status.equals(d.getStatus()))
            .filter(d -> phase == null || phase.isBlank() || phase.equals(d.getEscalationPhase()))
            .map(NoShowDispute::toDto)
            .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Résolution (resto en phase resto / support+admin en phase support)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Résout une contestation ({@code accepted}/{@code refused}).
     * <ul>
     *   <li>Phase {@code resto} : seuls le staff actif du restaurant concerné ou un admin.</li>
     *   <li>Phase {@code support} : seuls le support / admin (pas le restaurant — escaladé).</li>
     * </ul>
     * {@code accepted} → publie {@link NoShowDisputeResolvedEvent} (loyalty reverse la
     * pénalité). {@code refused} → publie aussi l'event (accepted=false) : loyalty ignore,
     * mais l'event reste utile pour la traçabilité / notifications.
     */
    @Transactional
    public NoShowDisputeDto resolve(UUID disputeId, ResolveDisputeDto dto) {
        NoShowDispute dispute = disputeRepository.findById(disputeId)
            .orElseThrow(() -> new NotFoundException("NoShowDispute", disputeId));

        if (!"pending".equals(dispute.getStatus())) {
            throw new ConflictException("Cette contestation est déjà résolue (" + dispute.getStatus() + ")");
        }

        requireResolveAccess(dispute);

        boolean accepted = "accepted".equals(dto.status());
        dispute.setStatus(accepted ? "accepted" : "refused");
        dispute.setResolutionNote(dto.resolutionNote());
        dispute.setResolvedBy(SecurityHelper.currentUserId());
        dispute.setResolvedAt(Instant.now(clock));
        NoShowDispute saved = disputeRepository.save(dispute);

        // Frontière Modulith : on publie l'event, loyalty consomme (reversal si accepted).
        eventPublisher.publishEvent(new NoShowDisputeResolvedEvent(
            saved.getId(), saved.getReservationId(), saved.getClientId(),
            saved.getRestaurantId(), accepted));

        return saved.toDto();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ABAC helpers
    // ═══════════════════════════════════════════════════════════════════════

    /** Lecture : client-owner de la résa OU staff actif du resto OU admin. */
    private void requireReadAccess(Reservation r) {
        UUID current = SecurityHelper.currentUserId();
        if (current == null) throw new ForbiddenException("Authentification requise");
        if (current.equals(r.getClientId())) return;
        if (restaurantAccessGuard.isAdminOrActiveStaffOf(r.getRestaurantId())) return;
        throw new ForbiddenException(
            "Accès interdit : ni client de la réservation, ni staff du restaurant, ni admin");
    }

    /** Résolution : selon la phase de la contestation (resto → staff resto/admin ; support → admin). */
    private void requireResolveAccess(NoShowDispute dispute) {
        UUID current = SecurityHelper.currentUserId();
        if (current == null) throw new ForbiddenException("Authentification requise");
        if (SecurityHelper.isAdmin()) return; // admin / support tranche en toute phase

        if (PHASE_RESTO.equals(dispute.getEscalationPhase())) {
            // Phase resto : le restaurant (staff actif) peut trancher SA contestation.
            if (restaurantAccessGuard.isAdminOrActiveStaffOf(dispute.getRestaurantId())) return;
            throw new ForbiddenException(
                "Accès interdit : seul le staff du restaurant ou un admin peut résoudre en phase 'resto'");
        }
        // Phase support : escaladé hors du restaurant → admin / support uniquement.
        throw new ForbiddenException(
            "Accès interdit : en phase 'support', seule l'équipe support / admin peut résoudre");
    }

    /** IDs des restaurants dont le user est staff actif (scope dashboard non-admin). */
    @SuppressWarnings("unchecked")
    private List<UUID> activeStaffRestaurantIds(UUID userId) {
        return entityManager.createNativeQuery("""
                SELECT restaurant_id FROM restaurant_staffs
                 WHERE user_id = :uid AND deleted_at IS NULL
                """)
            .setParameter("uid", userId)
            .getResultList();
    }
}
