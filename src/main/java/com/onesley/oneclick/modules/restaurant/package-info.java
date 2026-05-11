/**
 * Module {@code modules/restaurant} — catalogue restaurants partenaires (§4).
 *
 * <h3>Tables</h3>
 * <ul>
 *   <li>{@code restaurants} — fiche restaurant (nom, adresse, géo, status)</li>
 *   <li>{@code restaurant_staffs} — junction user × restaurant avec role_code</li>
 *   <li>{@code restaurant_zones} — zones physiques (Terrasse, Salle, Bar)</li>
 *   <li>{@code restaurant_tables} — tables par zone</li>
 *   <li>{@code restaurant_services} — créneaux service (brunch, dîner)</li>
 *   <li>{@code business_hours} — horaires polymorphiques (remplace opening_hours jsonb legacy)</li>
 * </ul>
 */
@ApplicationModule(displayName = "modules/restaurant", allowedDependencies = {"core/identity", "core/tenant"})
package com.onesley.oneclick.modules.restaurant;

import org.springframework.modulith.ApplicationModule;
