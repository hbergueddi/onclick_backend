package com.onesley.oneclick.core.ai.api;

import java.util.Map;
import java.util.UUID;

/**
 * Fragment de document indexable — objet métier du domaine {@code core/ai}.
 *
 * <p>Unité de contenu produite par un {@link Chunker} puis stockée/retrouvée via le
 * {@link VectorStore}. {@code id} est nul tant que le fragment n'est pas persisté (le store attribue
 * l'identifiant à l'insertion) et renseigné pour un fragment relu.
 *
 * @param id       identifiant persistant (nul avant stockage)
 * @param content  texte du fragment
 * @param metadata métadonnées libres (source, page, index de chunk…) — jamais nul
 */
public record DocumentChunk(UUID id, String content, Map<String, Object> metadata) {

    public DocumentChunk {
        metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
    }

    /** Fragment neuf (non encore persisté) — sans identifiant. */
    public static DocumentChunk of(String content, Map<String, Object> metadata) {
        return new DocumentChunk(null, content, metadata);
    }
}
