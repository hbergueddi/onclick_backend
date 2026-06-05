/**
 * Module {@code modules/stories} — « Stories » (PCC), contenu éphémère type Instagram
 * (un staff/owner publie, les membres du tenant visionnent).
 *
 * <p>Un staff de gestion du tenant (Adil pour PCC) publie une « story » ({@code mediaUrl} image/vidéo
 * + {@code caption} courte + {@code durationS} d'affichage + {@code sortOrder} + {@code publishAt}
 * programmable + {@code expiresAt} optionnel). Les membres du tenant ({@code CLIENT}) la visionnent
 * dans un carrousel éphémère tant qu'elle est <b>vivante</b> ({@code publish_at <= now} ET
 * ({@code expires_at} NULL ou futur) ET non soft-deletée). Le staff édite/supprime (soft-delete).
 * Workflow {@code créée → programmée → expirée → supprimée}.
 *
 * <p>Port du legacy Supabase ({@code usePccStories.ts} / {@code TenantStories.tsx} /
 * {@code StoryViewer.tsx}). Dans le legacy, les stories surchargeaient {@code tenant_events}
 * ({@code status='actif'} + {@code visible_until}) ; ici on dédie une table {@code pcc_stories}
 * (contenu story pur, découplé des events). Différences senior : <b>GÉNÉRIQUE</b> (tenant-agnostic
 * via le feature flag {@code tenants.features.has_stories} + scope du caller, jamais
 * {@code slug='palmeraie'} en dur) et <b>0 trigger SQL</b> (la logique visibilité/soft-delete vit
 * dans {@code PccStoryService}, testable et traçable).
 *
 * <h3>Pas de temps réel (documenté)</h3>
 * <p>Une story est du <b>CONTENU</b>, pas un dashboard KPI : la lecture membre est un fetch normal
 * (carrousel rafraîchi au mount, comme le legacy {@code staleTime} 30 s côté front). Aucun WebSocket
 * STOMP ni polling backend n'est requis. Aucun event de notification non plus (le tracking de vues /
 * unread du legacy {@code story_views} est hors scope V1 — ré-introductible plus tard via une table
 * dédiée séparée, sans casser ce module). Conséquence : ce module ne dépend ni de {@code realtime}
 * ni de {@code shared} (events).
 *
 * <h3>Modulith CLOSED — discipline architecturale</h3>
 * <ul>
 *   <li>{@code api/} — DTOs exposés hors module ({@code PccStoryDtos}).</li>
 *   <li>{@code internal/} — entité JPA {@code PccStory} + repo + service.</li>
 *   <li>Lectures cross-module (pattern P2 « hybride core-only ») :
 *     <ul>
 *       <li>users (résolution nom auteur + tenant du caller) → contrat typé
 *           {@code core.identity.api.UserDirectoryApi} (core.identity est OPEN) ;</li>
 *       <li>« staff actif du tenant » → <b>read-view SQL native</b> dans le repo (tables
 *           {@code restaurant_staffs}/{@code restaurants} en {@code nativeQuery}) — invariant
 *           « 0 dépendance business↔business », jamais d'import de {@code modules.restaurant}
 *           (pattern P2.c, identique à {@code modules/announcement} et {@code modules/feedback}).</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.stories",
    displayName = "modules/stories",
    allowedDependencies = {"core.identity", "audit", "exception", "security"}
)
package com.onesley.oneclick.modules.stories;

import org.springframework.modulith.ApplicationModule;
