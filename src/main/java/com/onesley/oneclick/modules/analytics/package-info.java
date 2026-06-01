/**
 * Module {@code modules/analytics} — vues admin agrégées (stats exécutives, wallet pool,
 * cockpit HI, rollups groupe) §19.
 *
 * <h3>Read-model CQRS — exception architecturale DOCUMENTÉE (P2)</h3>
 * <p>analytics <b>n'a pas de tables propres</b> : c'est le côté lecture (CQRS) de la
 * plateforme. Ses requêtes croisent en SQL natif les tables d'autres modules
 * ({@code reservations}, {@code loyalty_*}, {@code scanned_tickets}, {@code restaurants},
 * {@code oneclick_hi_invoices}…). <b>Ce SQL natif cross-table est son rôle légitime, PAS
 * une violation Modulith</b> — le réécrire en chaînes d'appels query-API dégraderait des
 * agrégations groupées en fan-out N-tables + jointures Java.
 *
 * <h3>Invariant plateforme (vérifié — voir P2)</h3>
 * <ul>
 *   <li>Les modules <b>business</b> ({@code restaurant}, {@code loyalty}, {@code promotion},
 *       {@code reservation}, {@code financial}, {@code oneclickhi}, analytics…) n'ont
 *       <b>aucune dépendance inter-module business</b> : ils ne dépendent que de {@code core.*}
 *       + infra OPEN ({@code audit}, {@code security}, {@code exception}, {@code shared},
 *       {@code realtime}).</li>
 *   <li>Lectures cross-module → <b>read-views natives</b> (SQL délibéré, découplage de schéma).</li>
 *   <li>Signaux cross-module → <b>events</b> (in-process via Modulith).</li>
 *   <li>Seule exception convertie en contrat typé (P2 « hybride core-only ») : les lectures
 *       vers les modules <b>fondationnels</b> {@code core.identity} (noms/email via
 *       {@code UserDirectoryApi}) / {@code core.tenant}, où un contrat &gt; SQL et reste dans
 *       le graphe de dépendances autorisé.</li>
 * </ul>
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    id = "modules.analytics",
    displayName = "modules/analytics",
    allowedDependencies = {"core.tenant", "audit", "exception", "security", "shared", "realtime"}
)
package com.onesley.oneclick.modules.analytics;

import org.springframework.modulith.ApplicationModule;
