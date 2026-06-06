/**
 * Module {@code modules/seminar} — « Séminaires » (PCC), demandes de devis B2B membre → commercial.
 *
 * <p>Un membre soumet une demande de séminaire / événement d'entreprise (entreprise, contact,
 * nombre de participants, dates souhaitées, besoins). Le commercial (staff/admin du tenant) traite
 * la demande via un workflow de statut {@code demandee → en_traitement → devis_envoye →
 * confirmee | refusee | annulee} et consigne des notes internes. À chaque changement de statut, le
 * membre organisateur est notifié.
 *
 * <p>Port fidèle du legacy Supabase ({@code seminar_requests} + RPC {@code create_seminar_request}
 * / {@code update_seminar_status} + Edge Function {@code send-pcc-seminar-status-update}).
 * Différence senior : GÉNÉRIQUE — le scope tenant est celui <b>du caller</b> (résolu via
 * {@code UserDirectoryApi}) et l'inbox commercial est tenant-wide (tout staff/admin actif du
 * tenant), pas {@code slug='palmeraie'} en dur → réutilisable par tout tenant whitelabel.
 *
 * <h3>Modulith CLOSED — discipline architecturale</h3>
 * <ul>
 *   <li>{@code api/} — DTOs exposés hors module ({@code SeminarDtos}).</li>
 *   <li>{@code internal/} — entité JPA {@code SeminarRequest} + repo + service + STOMP publisher.</li>
 *   <li>Lectures cross-module (pattern P2 « hybride core-only ») :
 *     <ul>
 *       <li>users (résolution noms/contact + tenant du caller) → contrat typé
 *           {@code core.identity.api.UserDirectoryApi} (core.identity est OPEN) ;</li>
 *       <li>destinataires notif « nouvelle demande » (staff/admins du tenant) → <b>read-view SQL
 *           native</b> dans le repo (tables {@code users}/{@code roles} en {@code nativeQuery}) —
 *           invariant « 0 dépendance business↔business » (pattern P2.c).</li>
 *     </ul>
 *   </li>
 *   <li>Communication out :
 *     <ul>
 *       <li>events {@link com.onesley.oneclick.shared.events.SeminarRequestedEvent} /
 *           {@link com.onesley.oneclick.shared.events.SeminarStatusChangedEvent} (in-process via
 *           Modulith) consommés par {@code core.notification} pour notifier commercial / membre ;</li>
 *       <li>temps réel {@code SeminarPublisher} (STOMP) : {@code /topic/seminars} (inbox commercial)
 *           + {@code /topic/seminars/user/{organizerId}} (avancement membre).</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.seminar",
    displayName = "modules/seminar",
    allowedDependencies = {"core.identity", "audit", "exception", "shared", "security", "realtime"}
)
package com.onesley.oneclick.modules.seminar;

import org.springframework.modulith.ApplicationModule;
