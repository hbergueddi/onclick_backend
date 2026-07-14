package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.AiQuery;
import com.onesley.oneclick.core.ai.api.ContextFragment;
import com.onesley.oneclick.core.ai.api.DocumentChunk;
import com.onesley.oneclick.core.ai.api.EmbeddingService;
import com.onesley.oneclick.core.ai.api.EmbeddingVector;
import com.onesley.oneclick.core.ai.api.SearchResult;
import com.onesley.oneclick.core.ai.api.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests unitaires de {@link RagContextSource} : mapping + résilience best-effort. */
class RagContextSourceTest {

    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final RagContextSource source = new RagContextSource(embeddingService, vectorStore);

    @Test
    void name_isRag() {
        assertThat(source.name()).isEqualTo("rag");
    }

    @Test
    void retrieve_mapsSearchResultsToFragments() {
        when(embeddingService.embed(any())).thenReturn(EmbeddingVector.of(new float[]{0.1f, 0.2f}));
        DocumentChunk chunk = new DocumentChunk(UUID.randomUUID(), "Politique d'annulation : 50 MAD.", Map.of("source", "faq"));
        when(vectorStore.search(any(), anyInt())).thenReturn(List.of(new SearchResult(chunk, 0.87)));

        List<ContextFragment> frags = source.retrieve(AiQuery.of("frais d'annulation ?"));

        assertThat(frags).hasSize(1);
        assertThat(frags.get(0).text()).contains("50 MAD");
        assertThat(frags.get(0).score()).isEqualTo(0.87);
    }

    @Test
    void retrieve_bestEffort_returnsEmptyOnFailure() {
        when(embeddingService.embed(any())).thenThrow(new RuntimeException("ollama down"));
        assertThat(source.retrieve(AiQuery.of("question"))).isEmpty(); // ne casse pas le chat
    }
}
