package com.onesley.oneclick.modules.restaurant.api;

/**
 * BE-4 (plan RESTAURANT-ONBOARDING) — suggestion d'autocomplétion Google Places, <b>sanitizée</b>.
 *
 * <p>Réponse publique de {@code GET /api/places/search} (formulaire d'inscription resto). On
 * n'expose QUE ce dont le wizard a besoin pour pré-remplir ses champs — pas de {@code place_id},
 * pas de coordonnées brutes, pas de payload Google complet (minimisation + ToS-friendly).
 *
 * @param displayName        nom commercial proposé par Google
 * @param formattedAddress   adresse complète formatée
 * @param nationalPhoneNumber téléphone national (nullable)
 * @param city               ville déduite des composants d'adresse (nullable)
 * @param cuisine            cuisine FR déduite des {@code types} Google (nullable)
 */
public record PlaceSuggestionDto(
    String displayName,
    String formattedAddress,
    String nationalPhoneNumber,
    String city,
    String cuisine
) {}
