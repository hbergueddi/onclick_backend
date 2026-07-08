package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.EmbeddingService;
import com.onesley.oneclick.core.ai.api.EmbeddingVector;
import com.onesley.oneclick.exception.BadRequestException;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implémentation Ollama du port {@link EmbeddingService} (module {@code core/ai}).
 *
 * <p>Adaptateur au-dessus du {@link EmbeddingModel} LangChain4j câblé par {@link OllamaConfig} :
 * convertit les types LangChain4j ({@code Embedding}, {@code TextSegment}) vers les objets métier du
 * domaine ({@link EmbeddingVector}). Aucune fuite de LangChain4j au-delà de cette classe.
 *
 * <p>Observabilité : chaque appel est chronométré via {@link AiMetrics} (durée + erreurs). Logs
 * techniques uniquement (nombre de textes, dimension), jamais le contenu encodé.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class OllamaEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final AiMetrics metrics;

    @Override
    public EmbeddingVector embed(String text) {
        if (text == null || text.isBlank()) {
            throw new BadRequestException("Le texte à encoder ne peut pas être vide");
        }
        return metrics.recordEmbedSingle(() ->
            EmbeddingVector.of(embeddingModel.embed(text).content().vector()));
    }

    @Override
    public List<EmbeddingVector> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        return metrics.recordEmbedBatch(() -> {
            List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();
            List<EmbeddingVector> vectors = embeddingModel.embedAll(segments).content().stream()
                .map(e -> EmbeddingVector.of(e.vector()))
                .toList();
            log.debug("[core/ai] embedded {} segment(s)", vectors.size());
            return vectors;
        });
    }

    @Override
    public int dimension() {
        return embeddingModel.dimension();
    }
}
