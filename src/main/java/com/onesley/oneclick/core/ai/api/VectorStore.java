package com.onesley.oneclick.core.ai.api;

import java.util.List;
import java.util.UUID;

/**
 * Port de <b>stockage vectoriel</b> exposé par {@code core/ai} — persiste des {@link DocumentChunk} avec
 * leur {@link EmbeddingVector} et effectue la recherche par similarité ({@link SearchResult}).
 *
 * <p>Contrat <b>indépendant de PostgreSQL / pgvector</b> et de tout ORM : n'expose que des objets
 * métier et types JDK. L'implémentation actuelle est {@code PgVectorStore} (Postgres + pgvector), mais
 * le domaine dépend de cette interface — remplacer le backend (autre base vectorielle, service managé)
 * se fait en fournissant une autre implémentation, sans modifier les appelants.
 */
public interface VectorStore {

    /**
     * Persiste un fragment et son embedding.
     *
     * @param chunk     fragment à stocker (son {@code id} éventuel est ignoré : le store en attribue un)
     * @param embedding vecteur d'embedding du contenu du fragment
     * @return l'identifiant du fragment créé
     */
    UUID add(DocumentChunk chunk, EmbeddingVector embedding);

    /**
     * Recherche les {@code topK} fragments les plus proches d'un vecteur de requête (similarité cosinus).
     *
     * @param queryEmbedding vecteur de la requête
     * @param topK           nombre maximum de résultats
     * @return les correspondances, triées par score décroissant (le plus similaire en premier)
     */
    List<SearchResult> search(EmbeddingVector queryEmbedding, int topK);
}
