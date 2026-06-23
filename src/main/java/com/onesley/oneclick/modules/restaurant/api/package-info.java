/**
 * Interface nommée {@code api} du module {@code modules/restaurant} (CLOSED).
 *
 * <p>Expose explicitement le package {@code api} comme point d'accès cross-module : DTOs publics
 * ({@code RestaurantDto}, {@code RestaurantCreateDto}, …) + ports de provisioning
 * ({@code RestaurantProvisioningApi}). Conforme à la règle d'archi « cross-module uniquement via
 * api packages ou events » : un module métier déclarant {@code "modules.restaurant :: api"} dans ses
 * {@code allowedDependencies} peut consommer ces types (ex. {@code modules.store} à l'approbation
 * d'une demande d'inscription — BE-2). Le reste du module ({@code internal}) demeure fermé.
 */
@org.springframework.modulith.NamedInterface("api")
package com.onesley.oneclick.modules.restaurant.api;
