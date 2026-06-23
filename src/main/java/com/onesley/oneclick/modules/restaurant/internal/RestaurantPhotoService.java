package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.core.media.api.MediaAccessApi;
import com.onesley.oneclick.core.media.api.MediaDtos.MediaDto;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.api.RestaurantPhotoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Galerie photos « Identité visuelle » d'un restaurant (max 5 : 1 principale + 4).
 *
 * <h3>Responsabilités du domaine restaurant</h3>
 * <ul>
 *   <li>Quota strict ({@link #MAX_PHOTOS}) appliqué côté serveur.</li>
 *   <li>« Photo principale » = synchronisée sur {@code restaurants.image} → réutilisée
 *       partout (Explore, cartes réservation, récaps). Seule la fiche Spotlight affiche
 *       la galerie complète.</li>
 *   <li>Stockage / persistance délégués au module {@code core/media} via {@link MediaAccessApi}
 *       (port public, pas d'import {@code internal} cross-module — Modulith CLOSED).</li>
 * </ul>
 *
 * <p>La garde RBAC ({@code hasAuthority('UPDATE:RESTAURANTS')}) + ABAC propriétaire
 * ({@code RestaurantAccessGuard.requireAdminOrActiveStaffOf}) sont portées par le
 * contrôleur ; ce service vérifie en plus que le média ciblé appartient bien au
 * restaurant (anti-IDOR cross-restaurant).</p>
 */
@Service
@RequiredArgsConstructor
public class RestaurantPhotoService {

    /** Type d'entité polymorphe dans la table {@code medias}. */
    public static final String ENTITY_TYPE = "restaurant";
    /** Quota : 1 principale + 4 secondaires. */
    public static final int MAX_PHOTOS = 5;

    private final MediaAccessApi mediaAccess;
    private final RestaurantRepository restaurantRepository;

    /** Galerie de gestion (restaurateur) — principale d'abord. */
    @Transactional(readOnly = true)
    public List<RestaurantPhotoDto> list(UUID restaurantId) {
        Restaurant r = requireRestaurant(restaurantId);
        return toPhotoDtos(restaurantId, r.getImage());
    }

    /**
     * Galerie publique (Spotlight) — la principale connue est fournie par l'appelant
     * (le {@code restaurants.image} déjà chargé) pour éviter une relecture de l'entité.
     */
    @Transactional(readOnly = true)
    public List<RestaurantPhotoDto> publicList(UUID restaurantId, String primaryUrl) {
        return toPhotoDtos(restaurantId, primaryUrl);
    }

    /** Ajoute une photo (quota max 5). La 1re photo (ou {@code makePrimary}) devient la principale. */
    @Transactional
    public RestaurantPhotoDto upload(UUID restaurantId, MultipartFile file, boolean makePrimary) {
        Restaurant r = requireRestaurant(restaurantId);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Fichier photo manquant.");
        }
        long count = mediaAccess.countForEntity(ENTITY_TYPE, restaurantId);
        if (count >= MAX_PHOTOS) {
            throw new BadRequestException("Maximum " + MAX_PHOTOS + " photos par restaurant.");
        }
        MediaDto media = mediaAccess.upload(ENTITY_TYPE, restaurantId, file, (int) count);
        boolean becomesPrimary = makePrimary || r.getImage() == null || r.getImage().isBlank();
        if (becomesPrimary) {
            r.setImage(media.url());
            restaurantRepository.save(r);
        }
        int sortOrder = media.sortOrder() == null ? 0 : media.sortOrder();
        return new RestaurantPhotoDto(media.id(), media.url(), sortOrder, becomesPrimary);
    }

    /** Définit la photo principale (synchronise {@code restaurants.image}). */
    @Transactional
    public List<RestaurantPhotoDto> setPrimary(UUID restaurantId, UUID mediaId) {
        Restaurant r = requireRestaurant(restaurantId);
        MediaDto media = requireOwnedMedia(restaurantId, mediaId);
        r.setImage(media.url());
        restaurantRepository.save(r);
        return toPhotoDtos(restaurantId, media.url());
    }

    /** Supprime une photo (soft-delete). Si c'était la principale → réaffectée à la suivante (ou null). */
    @Transactional
    public List<RestaurantPhotoDto> delete(UUID restaurantId, UUID mediaId) {
        Restaurant r = requireRestaurant(restaurantId);
        MediaDto media = requireOwnedMedia(restaurantId, mediaId);
        mediaAccess.delete(mediaId);
        if (media.url() != null && media.url().equals(r.getImage())) {
            List<MediaDto> remaining = mediaAccess.listForEntity(ENTITY_TYPE, restaurantId);
            r.setImage(remaining.isEmpty() ? null : remaining.get(0).url());
            restaurantRepository.save(r);
        }
        return toPhotoDtos(restaurantId, r.getImage());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private List<RestaurantPhotoDto> toPhotoDtos(UUID restaurantId, String primaryUrl) {
        return mediaAccess.listForEntity(ENTITY_TYPE, restaurantId).stream()
            .map(m -> new RestaurantPhotoDto(
                m.id(), m.url(),
                m.sortOrder() == null ? 0 : m.sortOrder(),
                primaryUrl != null && primaryUrl.equals(m.url())))
            // Principale en tête, puis ordre d'origine (sort_order / created_at) stable.
            .sorted(Comparator.comparing(RestaurantPhotoDto::primary).reversed())
            .toList();
    }

    private Restaurant requireRestaurant(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Restaurant", restaurantId));
    }

    /** Anti-IDOR : le média doit appartenir au restaurant ciblé (entity_type + entity_id). */
    private MediaDto requireOwnedMedia(UUID restaurantId, UUID mediaId) {
        MediaDto media = mediaAccess.findById(mediaId);
        if (!ENTITY_TYPE.equals(media.entityType()) || !restaurantId.equals(media.entityId())) {
            throw new NotFoundException("Media", mediaId);
        }
        return media;
    }
}
