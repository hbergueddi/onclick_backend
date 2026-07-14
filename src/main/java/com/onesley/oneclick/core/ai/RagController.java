package com.onesley.oneclick.core.ai;

import com.onesley.oneclick.core.ai.api.RagIngestRequest;
import com.onesley.oneclick.core.ai.api.RagIngestResponse;
import com.onesley.oneclick.core.ai.api.RagIngestionApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint d'<b>ingestion RAG</b> : {@code POST /api/ai/rag/documents}.
 *
 * <p>Indexe un document texte (chunk → embedding → stockage vectoriel) dans la base de connaissance
 * du chatbot. Réservé aux administrateurs plateforme.
 */
@RestController
@RequestMapping("/api/ai/rag")
@Tag(name = "AI — RAG", description = "Ingestion de documents dans la base de connaissance du chatbot")
@RequiredArgsConstructor
public class RagController {

    private final RagIngestionApi ingestionService;

    @PostMapping("/documents")
    @Operation(summary = "Indexe un document texte dans la base RAG (chunk + embeddings + pgvector)")
    // Admin plateforme uniquement. TODO : remplacer par une autorité dédiée (ex. MANAGE:AI) une fois seedée.
    @PreAuthorize("hasAuthority('CREATE:USERS')")
    public RagIngestResponse ingest(@Valid @RequestBody RagIngestRequest req) {
        Map<String, Object> metadata = (req.source() == null || req.source().isBlank())
            ? Map.of() : Map.of("source", req.source());
        return new RagIngestResponse(ingestionService.ingest(req.content(), metadata));
    }
}
