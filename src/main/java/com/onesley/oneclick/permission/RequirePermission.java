package com.onesley.oneclick.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation métier : exige une permission ({@code menu.action}) pour exécuter
 * la méthode/classe annotée. Évaluée au runtime par {@link RequirePermissionAspect}.
 *
 * <p>Usage :
 * <pre>
 * &#064;RestController
 * public class RestaurantController {
 *
 *     &#064;DeleteMapping("/{id}")
 *     &#064;RequirePermission(menu = "restaurant", action = PermissionAction.DELETE)
 *     public void delete(@PathVariable UUID id) { ... }
 * }
 * </pre>
 *
 * <p>Si l'utilisateur n'a pas la permission, l'aspect lève {@link
 * org.springframework.security.access.AccessDeniedException} → HTTP 403 + ProblemDetails RFC 7807.
 *
 * <p>Co-existe avec {@link org.springframework.security.access.prepost.PreAuthorize} —
 * les deux peuvent être combinés (ex: {@code @PreAuthorize("hasRole('admin')")} au
 * niveau classe + {@code @RequirePermission} fine au niveau méthode).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequirePermission {
    String menu();
    PermissionAction action();
}
