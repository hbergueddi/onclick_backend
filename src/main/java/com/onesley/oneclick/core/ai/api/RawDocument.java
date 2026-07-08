package com.onesley.oneclick.core.ai.api;

import java.util.Map;

/**
 * Document brut chargé depuis une source (fichier, upload, URL…), avant découpage — domaine {@code core/ai}.
 *
 * <p>Produit par un {@link DocumentLoader}, consommé par un {@link Chunker} qui le découpe en
 * {@link DocumentChunk}. Neutre vis-à-vis du format d'origine (PDF, txt, HTML…).
 *
 * @param content  contenu textuel extrait
 * @param metadata métadonnées de source (nom de fichier, type, auteur…) — jamais nul
 */
public record RawDocument(String content, Map<String, Object> metadata) {

    public RawDocument {
        metadata = (metadata == null) ? Map.of() : Map.copyOf(metadata);
    }

    public static RawDocument of(String content) {
        return new RawDocument(content, Map.of());
    }
}
