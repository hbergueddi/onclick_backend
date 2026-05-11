/**
 * Module {@code core/configuration} — feature flags dynamiques + config cache (§19).
 *
 * <h3>Tables</h3>
 * <ul>
 *   <li>{@code feature_flags} — flags dynamiques (différents de {@code tenant_features} statiques par tenant)</li>
 *   <li>{@code feature_flag_targets} — targeting custom (par user, tenant, role)</li>
 *   <li>{@code cache_configurations} — TTL + max entries des caches (Redis / JVM)</li>
 * </ul>
 */
@ApplicationModule(displayName = "core/configuration")
package com.onesley.oneclick.core.configuration;

import org.springframework.modulith.ApplicationModule;
