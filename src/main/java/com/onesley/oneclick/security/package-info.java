/**
 * Utility package {@code security} — infrastructure transverse utilisée par tous les modules.
 *
 * <p>{@link ApplicationModule.Type#OPEN} : visible et utilisable par n'importe quel module
 * sans déclaration explicite dans {@code allowedDependencies}. Pattern standard pour le
 * code partagé non-métier (DTOs communs, helpers, exceptions, audit base class, etc.).
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "security (shared infra)")
package com.onesley.oneclick.security;

import org.springframework.modulith.ApplicationModule;
