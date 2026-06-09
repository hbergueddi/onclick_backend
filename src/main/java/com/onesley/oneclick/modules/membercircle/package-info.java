/**
 * Module {@code modules/membercircle} — « Circle » (réseau social privé membres), C4.8c.
 *
 * <p>Modération tenant-admin (C4.8c) : lister par tenant + statut, approuver, rejeter (motif),
 * supprimer (soft). <b>Flux membre A.1</b> (2026-06-07) : création (status=pending), feed des posts
 * approuvés (enrichi auteur + likes), toggle like. Commentaires + mentions = tranche A.2 future.
 *
 * <h3>Modulith CLOSED — discipline architecturale</h3>
 * <ul>
 *   <li>{@code api/} — DTOs exposés ({@code MemberPostDtos}).</li>
 *   <li>{@code internal/} — entité {@code MemberPost} + repo + service.</li>
 *   <li>Lecture cross-table users (nom/avatar auteur) = <b>read-view SQL native</b> dans le service
 *       (pattern P2.c, aucune dépendance Java business↔business).</li>
 *   <li>RBAC : modération gardée par {@code @PreAuthorize hasAuthority('VIEW/UPDATE/DELETE:TENANTS')}
 *       (portail tenant-admin SUPERADMIN-only) — pas de nouvelle ressource RBAC.</li>
 * </ul>
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.membercircle",
    displayName = "modules/membercircle",
    allowedDependencies = {"core.identity", "core.membership", "audit", "exception", "security", "shared"}
)
package com.onesley.oneclick.modules.membercircle;

import org.springframework.modulith.ApplicationModule;
