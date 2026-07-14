package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.Chunker;
import com.onesley.oneclick.core.ai.api.DocumentChunk;
import com.onesley.oneclick.core.ai.api.EmbeddingService;
import com.onesley.oneclick.core.ai.api.EmbeddingVector;
import com.onesley.oneclick.core.ai.api.RawDocument;
import com.onesley.oneclick.core.ai.api.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tests unitaires de {@link RagIngestionService} (chunk → embed → store), ports mockés. */
class RagIngestionServiceTest {

    private final Chunker chunker = mock(Chunker.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final RagIngestionService service = new RagIngestionService(chunker, embeddingService, vectorStore);

    private static EmbeddingVector vec() {
        return EmbeddingVector.of(new float[]{0.1f, 0.2f, 0.3f});
    }

    @Test
    void ingest_storesOneVectorPerChunk() {
        DocumentChunk c1 = DocumentChunk.of("chunk A", Map.of());
        DocumentChunk c2 = DocumentChunk.of("chunk B", Map.of());
        when(chunker.chunk(any(RawDocument.class))).thenReturn(List.of(c1, c2));
        when(embeddingService.embedAll(anyList())).thenReturn(List.of(vec(), vec()));

        int n = service.ingest("un long document", Map.of("source", "faq"));

        assertThat(n).isEqualTo(2);
        verify(vectorStore, times(2)).add(any(DocumentChunk.class), any(EmbeddingVector.class));
    }

    @Test
    void ingest_emptyChunks_storesNothing() {
        when(chunker.chunk(any(RawDocument.class))).thenReturn(List.of());
        assertThat(service.ingest("   ", Map.of())).isZero();
        verifyNoInteractions(vectorStore);
    }
}
