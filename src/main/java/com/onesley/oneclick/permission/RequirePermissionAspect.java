package com.onesley.oneclick.permission;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Aspect qui intercepte les méthodes/classes annotées {@link RequirePermission}
 * et lève {@link AccessDeniedException} si l'utilisateur courant n'a pas la
 * permission requise.
 *
 * <p>Précédence : exécuté APRÈS Spring Security (l'authentication doit être
 * établie avant d'arriver ici). Gérer l'auth absente : si pas d'authentication,
 * on lève {@code AccessDeniedException} également (le {@link
 * com.onesley.oneclick.exception.GlobalExceptionHandler} mappe en 403, qui
 * n'est pas optimal — idéalement 401, mais cohérent avec la sémantique
 * "permission denied").
 *
 * <p>Détection d'annotation : on cherche {@code @RequirePermission} d'abord au
 * niveau méthode, puis au niveau classe. La méthode prend le pas (override).
 */
@Aspect
@Component
public class RequirePermissionAspect {

    private final PermissionsService permissionsService;

    public RequirePermissionAspect(PermissionsService permissionsService) {
        this.permissionsService = permissionsService;
    }

    @Before("@annotation(com.onesley.oneclick.permission.RequirePermission) " +
            "|| @within(com.onesley.oneclick.permission.RequirePermission)")
    public void checkPermission(JoinPoint joinPoint) {
        RequirePermission perm = findAnnotation(joinPoint);
        if (perm == null) return;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!permissionsService.can(auth, perm.menu(), perm.action())) {
            throw new AccessDeniedException(
                "Permission denied: " + perm.menu() + "." + perm.action()
            );
        }
    }

    private RequirePermission findAnnotation(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        // Méthode d'abord (override de la classe)
        RequirePermission ann = method.getAnnotation(RequirePermission.class);
        if (ann != null) return ann;
        // Sinon classe
        return method.getDeclaringClass().getAnnotation(RequirePermission.class);
    }
}
