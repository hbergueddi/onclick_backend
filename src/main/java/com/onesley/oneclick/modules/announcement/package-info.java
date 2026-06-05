/**
 * Module {@code modules/announcement} — « Annonces tenant » (Lot 8), comm B2B staff descendante
 * (un tenant-admin publie, les owners/staff du tenant lisent et acquittent).
 *
 * <p>Un tenant-admin (Adil pour PCC) publie une annonce ({@code title} + {@code body} + priorité
 * {@code urgent}/{@code permanent} + épinglage + {@code publishAt} programmable + image optionnelle).
 * Le staff actif du tenant la lit dans sa bannière/feed et l'acquitte (mark-read par
 * {@code body_version}). Éditer le corps incrémente {@code body_version} et purge les acquittements
 * → ré-acquittement requis. Soft-delete réservé aux tenant-admins. Workflow {@code scheduled →
 * published → archived → deleted}.
 *
 * <p>Port fidèle du legacy Supabase ({@code tenant_announcements} + {@code announcement_reads} +
 * Edge Functions de gestion). Différences senior : <b>GÉNÉRIQUE</b> (tenant-agnostic via le feature
 * flag {@code tenants.features.has_announcements} + scope du caller, jamais {@code slug='palmeraie'}
 * en dur) et <b>0 trigger SQL</b> (la logique pinned/body_version/soft-delete vit dans
 * {@code AnnouncementService}, testable et traçable).
 *
 * <h3>Modulith CLOSED — discipline architecturale</h3>
 * <ul>
 *   <li>{@code api/} — DTOs exposés hors module ({@code AnnouncementDtos}).</li>
 *   <li>{@code internal/} — entités JPA {@code Announcement}/{@code AnnouncementRead} + repos +
 *       service + STOMP publisher.</li>
 *   <li>Lectures cross-module (pattern P2 « hybride core-only ») :
 *     <ul>
 *       <li>users (résolution nom auteur + tenant du caller) → contrat typé
 *           {@code core.identity.api.UserDirectoryApi} (core.identity est OPEN) ;</li>
 *       <li>« staff actif du tenant » + « tenant-admin » + destinataires notif → <b>read-views SQL
 *           natives</b> dans le repo (tables {@code restaurant_staffs}/{@code restaurants}/
 *           {@code users}/{@code roles} en {@code nativeQuery}) — invariant « 0 dépendance
 *           business↔business », jamais d'import de {@code modules.restaurant} (pattern P2.c).</li>
 *     </ul>
 *   </li>
 *   <li>Communication out :
 *     <ul>
 *       <li>event {@link com.onesley.oneclick.shared.events.AnnouncementPublishedEvent} (in-process
 *           via Modulith) consommé par {@code core.notification} pour notifier le staff (in-app) —
 *           destinataires résolus ici et portés sur l'event (le module notification est CLOSED) ;</li>
 *       <li>temps réel {@code AnnouncementPublisher} (STOMP) : {@code /topic/announcements/{tenantId}}
 *           (création / publication / édition).</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h3>V1 hors scope (documenté)</h3>
 * <ul>
 *   <li><b>Push différé programmé</b> (cron pg_cron dépilant les {@code publish_at} futurs) : la
 *       visibilité est déjà gérée par le filtre {@code publish_at <= now} de la lecture staff ; une
 *       annonce programmée devient visible automatiquement à sa date (sans push proactif). Les
 *       colonnes {@code push_sent_at} et l'index dédié sont posés pour cette évolution.</li>
 *   <li><b>Upload d'image</b> : {@code image_url} est une string ; l'upload passe par le
 *       {@code mediaService} existant côté front (le module ne gère pas le binaire).</li>
 * </ul>
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.announcement",
    displayName = "modules/announcement",
    allowedDependencies = {"core.identity", "audit", "exception", "shared", "security", "realtime"}
)
package com.onesley.oneclick.modules.announcement;

import org.springframework.modulith.ApplicationModule;
