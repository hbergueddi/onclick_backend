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
 * <h3>API exposée aux autres modules</h3>
 * <p>Aucun. Les autres modules communiquent par events (UserCreatedEvent, etc.)
 * et peuvent référencer {@code user.id} (UUID) mais pas l'entité {@link com.onesley.oneclick.core.identity.User}.
 */
@ApplicationModule(displayName = "core/identity")
package com.onesley.oneclick.core.identity;

import org.springframework.modulith.ApplicationModule;
