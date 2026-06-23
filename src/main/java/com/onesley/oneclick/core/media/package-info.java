/**
 * Module {@code core/media} — médias et fichiers polymorphiques (§14).
 *
 * <h3>Pattern</h3>
 * <p>Polymorphique via {@code entity_type + entity_id} — remplace les tables
 * spécifiques héritées de Supabase (restaurant_media, avatars, community_covers,
 * ticket-photos, etc.).
 *
 * <p><b>Type.OPEN</b> (comme {@code core.identity} / {@code core.tenant}) : module utilitaire
 * {@code core} consommé par des modules métier — ex. {@code modules.restaurant} accède au port
 * {@code MediaAccessApi} (galerie photos « Identité visuelle »). Aligné sur le pattern des autres
 * sous-modules {@code core} exposés (référencés par id simple dans les {@code allowedDependencies}).
 */
@ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.OPEN,
    id = "core.media",
    displayName = "core/media",
    allowedDependencies = {"core.identity", "audit", "exception", "security", "shared"}
)
package com.onesley.oneclick.core.media;

import org.springframework.modulith.ApplicationModule;
