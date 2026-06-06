/**
 * Module {@code modules/membercircle} — « Circle » (réseau social privé membres), C4.8c.
 *
 * <p>Cette tranche ne porte QUE la <b>modération tenant-admin</b> des posts membres : lister par
 * tenant + statut (pending/approved/rejected), approuver, rejeter (motif), supprimer (soft). Le flux
 * membre complet (création, feed, likes, commentaires, mentions, tags) du legacy {@code member_circle}
 * est un lot futur séparé.
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
    allowedDependencies = {"audit", "exception", "shared"}
)
package com.onesley.oneclick.modules.membercircle;

import org.springframework.modulith.ApplicationModule;
