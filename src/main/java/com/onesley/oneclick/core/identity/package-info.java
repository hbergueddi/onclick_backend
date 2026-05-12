/**
 * Module {@code core/identity} — RBAC simplifié (1 user = 1 rôle).
 *
 * <h3>Tables</h3>
 * <ul>
 *   <li>{@code users} — identités applicatives</li>
 *   <li>{@code roles} — ADMIN / RESTAURATEUR / STAFF / CLIENT / TENANT_ADMIN / ...</li>
 *   <li>{@code menus} — arbre de menus applicatifs (sidebar)</li>
 *   <li>{@code actions} — actions métier (CREATE_RESERVATION, CANCEL_RESERVATION, ...)</li>
 *   <li>{@code permissions} — junction role × (menu | action)</li>
 * </ul>
 *
 * <h3>API exposée (Type.CLOSED — Sprint C.3)</h3>
 * <ul>
 *   <li>{@code api/User} — Entity de plateforme, exposée pour @ManyToOne cross-module</li>
 *   <li>{@code api/Role} — Entity de plateforme, exposée pour User.role</li>
 *   <li>{@code api/UserDto, UserCreateDto, UserUpdateDto} — DTOs publics</li>
 * </ul>
 * Les entities {@code internal/Permission, Menu, Action} restent privées.
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.OPEN,
    id = "core.identity",
    displayName = "core/identity"
)
package com.onesley.oneclick.core.identity;

import org.springframework.modulith.ApplicationModule;
