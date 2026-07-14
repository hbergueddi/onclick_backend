package com.onesley.oneclick.core.ai.internal;

import com.onesley.oneclick.core.ai.api.DocumentChunk;
import com.onesley.oneclick.core.ai.api.RawDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests unitaires de {@link SimpleChunker}. */
class SimpleChunkerTest {

    private final SimpleChunker chunker = new SimpleChunker();

    @Test
    void chunk_emptyOrNull_returnsEmpty() {
        assertThat(chunker.chunk(new RawDocument("   ", Map.of()))).isEmpty();
        assertThat(chunker.chunk(RawDocument.of(""))).isEmpty();
    }

    @Test
    void chunk_shortText_singleChunk_withMetadataAndIndex() {
        List<DocumentChunk> chunks = chunker.chunk(new RawDocument("Bonjour le monde.", Map.of("source", "faq")));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo("Bonjour le monde.");
        assertThat(chunks.get(0).metadata()).containsEntry("source", "faq").containsEntry("chunkIndex", 0);
    }

    @Test
    void chunk_longText_splitsIntoMultipleChunks_withIncreasingIndex() {
        String longText = "phrase. ".repeat(400); // ~3200 chars > CHUNK_SIZE
        List<DocumentChunk> chunks = chunker.chunk(RawDocument.of(longText));
        assertThat(chunks.size()).isGreaterThan(1);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).metadata()).containsEntry("chunkIndex", i);
            assertThat(chunks.get(i).content().length()).isLessThanOrEqualTo(800);
        }
    }
}
