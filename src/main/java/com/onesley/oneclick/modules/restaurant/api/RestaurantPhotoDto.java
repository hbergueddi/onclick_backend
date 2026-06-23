package com.onesley.oneclick.modules.restaurant.api;

import java.util.UUID;

/**
 * DTO public d'une photo de restaurant (galerie « Identité visuelle », max 5).
 *
 * <p>Adossé à la table polymorphe {@code medias} ({@code entity_type='restaurant'}).
 * La photo {@code primary} est celle synchronisée sur {@code restaurants.image}
 * (affichée partout : Explore, cartes réservation, récaps). La galerie complète
 * (principale + secondaires) n'est exposée que sur la fiche Spotlight.</p>
 */
public record RestaurantPhotoDto(
    UUID id,
    String url,
    int sortOrder,
    boolean primary
) {
}
