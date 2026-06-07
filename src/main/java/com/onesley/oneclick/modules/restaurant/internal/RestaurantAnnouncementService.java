package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.RestaurantAccessGuard;
import com.onesley.oneclick.security.SecurityHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantAnnouncementDto;
import lombok.RequiredArgsConstructor;

/**
 * Service des annonces éphémères 24h par restaurant (Gap #6 — port legacy 14/05).
 *
 * <p>Logique métier (sans trigger SQL, cohérent avec le module announcement) :
 * <ul>
 *   <li>{@link #getActive} — lecture publique de l'annonce active (filtre {@code expires_at > now}).</li>
 *   <li>{@link #create} — ABAC (staff actif du resto OU admin) ; pose {@code expires_at = now + 24h} ;
 *       expire les autres annonces du resto (1 seule active à la fois).</li>
 *   <li>{@link #delete} — ABAC ; arrêt immédiat (suppression).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RestaurantAnnouncementService {

    private static final Duration TTL = Duration.ofHours(24);
    private static final int MAX_MESSAGE = 280;

    private final RestaurantAnnouncementRepository repository;
    private final RestaurantAccessGuard accessGuard;
    private final Clock clock;

    /** Annonce active d'un resto, ou {@code null} si aucune (lecture publique). */
    public RestaurantAnnouncementDto getActive(UUID restaurantId) {
        return repository
            .findFirstByRestaurantIdAndExpiresAtAfterOrderByCreatedAtDesc(restaurantId, clock.instant())
            .map(RestaurantAnnouncement::toDto)
            .orElse(null);
    }

    /**
     * Publie une annonce 24h pour un resto (staff actif ou admin). Remplace toute
     * annonce active existante du même resto (single-active).
     */
    @Transactional
    public RestaurantAnnouncementDto create(UUID restaurantId, String message) {
        accessGuard.requireAdminOrActiveStaffOf(restaurantId);
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Le message ne peut pas être vide");
        }
        if (trimmed.length() > MAX_MESSAGE) {
            throw new BadRequestException("Le message ne peut pas dépasser " + MAX_MESSAGE + " caractères");
        }
        Instant now = clock.instant();
        RestaurantAnnouncement saved = repository.save(new RestaurantAnnouncement(
            UUID.randomUUID(), restaurantId, trimmed, SecurityHelper.currentUserId(),
            now, now.plus(TTL)));
        // Single-active : expire toutes les autres annonces actives de ce resto.
        repository.expireOthers(restaurantId, saved.getId(), now);
        return saved.toDto();
    }

    /** Arrête (supprime) une annonce — ABAC sur le resto propriétaire. */
    @Transactional
    public void delete(UUID announcementId) {
        RestaurantAnnouncement a = repository.findById(announcementId)
            .orElseThrow(() -> new NotFoundException("RestaurantAnnouncement", announcementId));
        accessGuard.requireAdminOrActiveStaffOf(a.getRestaurantId());
        repository.delete(a);
    }
}
