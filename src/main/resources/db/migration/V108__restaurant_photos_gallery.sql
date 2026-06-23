-- V108 — Galerie photos restaurant (« Identité visuelle », max 5 : principale + 4).
--
-- Pas de nouvelle table : on réutilise la table polymorphe `medias` (V2) avec
-- entity_type = 'restaurant' (entity_id = restaurants.id). La photo « principale »
-- reste synchronisée sur restaurants.image (déjà utilisée partout : Explore, cartes
-- réservation, récaps). Seule la fiche Spotlight expose la galerie complète via
-- RestaurantDto.photos.
--
-- Pas de nouvelle autorité RBAC : la gestion réutilise UPDATE:RESTAURANTS (mutations)
-- + VIEW:RESTAURANTS (lecture de gestion) + ABAC RestaurantAccessGuard. La lecture
-- client (Spotlight) passe par le catalogue public (GET /api/restaurants/{id}).
--
-- Cette migration ajoute uniquement un index de lecture partiel pour la requête
-- « photos actives d'un restaurant, ordonnées » (RestaurantPhotoService.toPhotoDtos).
CREATE INDEX IF NOT EXISTS idx_medias_restaurant_active
    ON medias (entity_id, sort_order, created_at)
    WHERE entity_type = 'restaurant' AND deleted_at IS NULL;
