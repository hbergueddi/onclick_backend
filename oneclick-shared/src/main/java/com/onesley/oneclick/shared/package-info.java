/**
 * Module Maven {@code oneclick-shared} — DTOs partagés inter-services.
 *
 * <p>Marqué {@link ApplicationModule.Type#OPEN} pour permettre aux modules
 * consommateurs ({@code modules.reservation}, etc.) d'importer librement les
 * events et DTOs sans déclaration explicite dans {@code allowedDependencies}.
 *
 * <p>Contenu :
 * <ul>
 *   <li>{@code com.onesley.oneclick.shared.events.*} — events Spring Modulith
 *       avec {@code @Externalized} pour publication Kafka cross-service</li>
 *   <li>{@link com.onesley.oneclick.shared.PageResponse} — wrapper pagination</li>
 * </ul>
 *
 * <p>Volontairement sans Spring Boot autoconfig pour rester consommable par
 * n'importe quel microservice sans tirer de dépendance lourde.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "shared (events + DTOs)")
package com.onesley.oneclick.shared;

import org.springframework.modulith.ApplicationModule;
