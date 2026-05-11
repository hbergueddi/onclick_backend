/**
 * Umbrella module {@code core} — regroupe les sous-modules infrastructure
 * (identity, auth, tenant, notification, media, audit_log, configuration).
 *
 * <p>{@link ApplicationModule.Type#OPEN} : ce parent module est ouvert pour
 * permettre les références croisées entre ses sous-modules ET depuis {@code modules.*}
 * (ex: {@code modules.reservation → core.identity}, courant en JPA cross-aggregate).
 *
 * <p>Phase 2 §21 spec senior : ces couplages JPA seront progressivement remplacés
 * par des events Spring Modulith ({@code @ApplicationModuleListener}) avant
 * extraction microservices. Pour l'instant on garde le type OPEN.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "core (umbrella)")
package com.onesley.oneclick.core;

import org.springframework.modulith.ApplicationModule;
