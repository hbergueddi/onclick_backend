package com.onesley.oneclick.modules.restaurant.api;

import java.util.UUID;

/**
 * BE-2 (plan RESTAURANT-ONBOARDING) — port de <b>provisioning</b> resto+gérant exposé hors module.
 *
 * <p>Permet à {@code modules.store} (à l'approbation d'une demande d'inscription) de créer la fiche
 * restaurant + le lien {@code restaurant_staffs(owner)} <b>sans dépendre des services internes</b>
 * du module restaurant. Le compte gérant (User) est provisionné en amont par
 * {@code core.identity} ({@code UserProvisioningApi}) ; son id est passé ici.
 */
public interface RestaurantProvisioningApi {

    /**
     * Données minimales pour créer la fiche restaurant + rattacher le gérant comme owner.
     *
     * @param tenantId    tenant du restaurant (obligatoire)
     * @param name        nom commercial
     * @param city        ville (obligatoire)
     * @param address     adresse (nullable)
     * @param phone       téléphone (nullable)
     * @param cuisine     type de cuisine (nullable)
     * @param ownerUserId id du compte gérant (déjà provisionné) à rattacher comme owner
     */
    record ProvisionCommand(
        UUID tenantId, String name, String city, String address, String phone, String cuisine, UUID ownerUserId) {}

    /**
     * Crée le restaurant et l'entrée {@code restaurant_staffs} (role_code {@code owner}) pour le gérant.
     * @return l'id du restaurant créé.
     */
    UUID provisionWithOwner(ProvisionCommand cmd);
}
