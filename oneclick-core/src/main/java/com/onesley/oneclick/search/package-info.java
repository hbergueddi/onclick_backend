/**
 * Utility package {@code search} — infrastructure transverse utilisée par tous les modules.
 *
 * <p>{@link ApplicationModule.Type#OPEN} : visible et utilisable par n'importe quel module
 * sans déclaration explicite dans {@code allowedDependencies}. Pattern standard pour le
 * code partagé non-métier (DTOs communs, helpers, exceptions, audit base class, etc.).
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "search (shared infra)")
package com.onesley.oneclick.search;

import org.springframework.modulith.ApplicationModule;
