package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.Chunker;
import com.onesley.oneclick.core.ai.api.DocumentChunk;
import com.onesley.oneclick.core.ai.api.RawDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Découpage par fenêtre glissante de caractères avec chevauchement — module {@code core/ai}.
 *
 * <p>Implémentation par défaut du port {@link Chunker} : suffisante pour du texte brut (le parsing
 * PDF/HTML viendra via un {@code DocumentLoader}). Chaque fragment reçoit les métadonnées de source
 * plus son {@code chunkIndex}. Remplaçable par une stratégie plus fine (par phrases/tokens) en
 * fournissant un autre {@code @Primary} {@link Chunker}.
 */
@Component
class SimpleChunker implements Chunker {

    /** Taille de fenêtre (caractères) et chevauchement — valeurs raisonnables pour de la doc courte. */
    private static final int CHUNK_SIZE = 800;
    private static final int OVERLAP = 100;

    @Override
    public List<DocumentChunk> chunk(RawDocument document) {
        String text = (document == null || document.content() == null) ? "" : document.content().strip();
        if (text.isEmpty()) return List.of();
        Map<String, Object> baseMeta = (document.metadata() == null) ? Map.of() : document.metadata();

        List<DocumentChunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            String piece = text.substring(start, end).strip();
            if (!piece.isEmpty()) {
                Map<String, Object> meta = new LinkedHashMap<>(baseMeta);
                meta.put("chunkIndex", index++);
                chunks.add(DocumentChunk.of(piece, meta));
            }
            if (end >= text.length()) break;
            start = end - OVERLAP; // chevauchement (CHUNK_SIZE > OVERLAP garantit la progression)
        }
        return chunks;
    }
}
