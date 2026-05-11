/**
 * Module Maven {@code oneclick-shared} — DTOs partagés inter-services.
 *
 * <p>Contient :
 * <ul>
 *   <li>{@code com.onesley.oneclick.shared.events.*} — events Spring Modulith
 *       avec {@code @Externalized} pour publication Kafka cross-service</li>
 *   <li>{@link com.onesley.oneclick.shared.PageResponse} — wrapper pagination
 *       standardisé pour les REST controllers</li>
 * </ul>
 *
 * <p>Ce JAR est volontairement minimal (pas de Spring Modulith {@code @ApplicationModule},
 * pas de JPA, pas de web auto-config) pour être consommé par n'importe quel microservice
 * sans tirer de dépendance lourde.
 */
package com.onesley.oneclick.shared;
