/**
 * Module {@code modules/feedback} — « Avis » (PCC Lot 7), thread membre ↔ owner (Adil).
 *
 * <p>Un membre envoie un avis ({@code sentiment} happy/unhappy + {@code category} libre +
 * {@code comment} optionnel), éventuellement ciblé sur un resto du tenant ({@code target_restaurant_id} ;
 * NULL = avis général tenant-wide). L'owner (Adil) répond UNE fois ; le membre marque la réponse
 * lue. Le thread est conservé. Workflow {@code created → replied}.
 *
 * <p>Port fidèle du legacy Supabase ({@code pcc_feedbacks} + Edge Functions
 * {@code send-pcc-feedback} / {@code send-pcc-feedback-reply}). Différence senior : GÉNÉRIQUE — le
 * scope tenant est celui <b>du caller</b> (résolu via {@code UserDirectoryApi}) et l'owner-scope
 * passe par une read-view native joignant {@code restaurant_staffs}/{@code restaurants} (schéma
 * Spring : {@code role_code='owner'} + soft-delete), pas par {@code slug='palmeraie'} en dur →
 * réutilisable par tout tenant whitelabel.
 *
 * <h3>Modulith CLOSED — discipline architecturale</h3>
 * <ul>
 *   <li>{@code api/} — DTOs exposés hors module ({@code PccFeedbackDtos}).</li>
 *   <li>{@code internal/} — entité JPA {@code PccFeedback} + repo + service + STOMP publisher.</li>
 *   <li>Lectures cross-module (pattern P2 « hybride core-only ») :
 *     <ul>
 *       <li>users (résolution noms/contact + tenant du caller) → contrat typé
 *           {@code core.identity.api.UserDirectoryApi} (core.identity est OPEN) ;</li>
 *       <li>owner-scope + nom du resto ciblé → <b>read-view SQL native</b> dans le repo (tables
 *           {@code restaurant_staffs}/{@code restaurants} en {@code nativeQuery}) — invariant
 *           « 0 dépendance business↔business », jamais d'import des modules {@code restaurant}/
 *           {@code loyalty} (pattern P2.c).</li>
 *     </ul>
 *   </li>
 *   <li>Communication out :
 *     <ul>
 *       <li>events {@link com.onesley.oneclick.shared.events.FeedbackCreatedEvent} /
 *           {@link com.onesley.oneclick.shared.events.FeedbackRepliedEvent} (in-process via
 *           Modulith) consommés par {@code core.notification} pour notifier owners / membre ;</li>
 *       <li>temps réel {@code FeedbackPublisher} (STOMP) : {@code /topic/pcc-feedbacks} (création,
 *           dashboard owner) + {@code /topic/pcc-feedbacks/user/{memberId}} (réponse, membre).</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.feedback",
    displayName = "modules/feedback",
    allowedDependencies = {"core.identity", "audit", "exception", "shared", "security", "realtime"}
)
package com.onesley.oneclick.modules.feedback;

import org.springframework.modulith.ApplicationModule;
