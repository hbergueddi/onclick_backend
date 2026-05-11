/**
 * Module {@code core/media} — médias et fichiers polymorphiques (§14).
 *
 * <h3>Pattern</h3>
 * <p>Polymorphique via {@code entity_type + entity_id} — remplace les tables
 * spécifiques héritées de Supabase (restaurant_media, avatars, community_covers,
 * ticket-photos, etc.).
 */
@ApplicationModule(displayName = "core/media", allowedDependencies = "core/identity")
package com.onesley.oneclick.core.media;

import org.springframework.modulith.ApplicationModule;
