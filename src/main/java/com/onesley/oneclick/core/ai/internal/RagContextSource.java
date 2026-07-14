package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.AiQuery;
import com.onesley.oneclick.core.ai.api.ContextFragment;
import com.onesley.oneclick.core.ai.api.ContextSource;
import com.onesley.oneclick.core.ai.api.EmbeddingService;
import com.onesley.oneclick.core.ai.api.EmbeddingVector;
import com.onesley.oneclick.core.ai.api.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Source de contexte <b>RAG</b> (name {@code "rag"}) — module {@code core/ai}.
 *
 * <p>Implémente le port {@link ContextSource} : embed la question ({@link EmbeddingService}) puis
 * recherche les fragments les plus proches ({@link VectorStore}), renvoyés comme {@link ContextFragment}.
 * L'orchestrateur la combine avec les autres sources sans la connaître.
 *
 * <p><b>Best-effort</b> : une panne d'embedding ou de recherche ne casse pas le chat — on log et on
 * renvoie un contexte vide (le LLM répond alors sans RAG).
 */
@Component
@RequiredArgsConstructor
@Slf4j
class RagContextSource implements ContextSource {

    /** Nombre de fragments injectés dans le contexte. */
    private static final int TOP_K = 4;

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    @Override
    public String name() {
        return "rag";
    }

    @Override
    public List<ContextFragment> retrieve(AiQuery query) {
        try {
            EmbeddingVector queryVector = embeddingService.embed(query.question());
            return vectorStore.search(queryVector, TOP_K).stream()
                .map(r -> new ContextFragment(r.chunk().content(), r.chunk().metadata(), r.score()))
                .toList();
        } catch (RuntimeException e) {
            log.warn("[core/ai] RAG indisponible (contexte ignoré) : {}", e.getMessage());
            return List.of();
        }
    }
}
