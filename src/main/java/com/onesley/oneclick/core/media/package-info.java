/**
 * Module {@code core/media} — médias et fichiers polymorphiques (§14).
 *
 * <h3>Pattern</h3>
 * <p>Polymorphique via {@code entity_type + entity_id} — remplace les tables
 * spécifiques héritées de Supabase (restaurant_media, avatars, community_covers,
 * ticket-photos, etc.).
 */
@ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN, id = "core.media", displayName = "core/media")
package com.onesley.oneclick.core.media;

import org.springframework.modulith.ApplicationModule;
