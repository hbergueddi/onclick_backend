/**
 * Module {@code modules/store} — workflow d'onboarding restaurants.
 *
 * <p>Gère les demandes d'inscription Store : capture du formulaire (nom resto,
 * owner, ville, etc.) → review admin → approval/rejection → création resto +
 * compte owner (via {@code modules/restaurant}).
 *
 * <p>Sprint I.3 — création initiale (port EF send-onboarding-request +
 * send-onboarding-decision).
 *
 * <p>Lots B4/B5 — notifications onboarding (événementiel, frontière Modulith) :
 * <ul>
 *   <li><b>B4</b> : à la <b>création</b> d'une demande, les <b>admins plateforme</b> (SUPERADMIN)
 *       sont résolus ICI via {@code core.identity} ({@code UserDirectoryApi.adminUserIds()}) et
 *       portés sur {@code StoreOnboardingRequestedEvent} → notif in-app admin (core.notification).
 *       Pattern {@code ContractExpiringSoonEvent} / {@code RestaurantReferralActivatedEvent} — d'où
 *       l'ajout de la dépendance {@code core.identity} (comme {@code modules.restaurant_referral}).</li>
 *   <li><b>B5</b> : à la <b>décision</b> (approuvée/refusée), {@code StoreOnboardingDecidedEvent}
 *       (porte email/nom/verdict/motif/lien) → email branded au gérant (core.email, hors frontière
 *       store — d'où l'event {@code shared} qui transporte tout le payload).</li>
 * </ul>
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.store",
    displayName = "modules/store",
    allowedDependencies = {"core.identity", "core.tenant", "core.notification", "modules.restaurant :: api", "audit", "exception", "security", "shared"}
)
package com.onesley.oneclick.modules.store;

import org.springframework.modulith.ApplicationModule;
