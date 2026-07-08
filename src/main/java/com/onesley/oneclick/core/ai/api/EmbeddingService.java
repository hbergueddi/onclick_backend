package com.onesley.oneclick.core.ai.api;

import java.util.List;

/**
 * Port d'<b>embedding</b> exposé par {@code core/ai} — transforme du texte en {@link EmbeddingVector}.
 *
 * <p>Contrat volontairement <b>indépendant de LangChain4j</b> et de tout provider : n'expose que des
 * objets métier ({@link EmbeddingVector}) et types JDK. L'implémentation actuelle est adossée à Ollama,
 * mais le domaine et le futur pipeline RAG dépendent de cette interface, jamais du provider concret —
 * passer à OpenAI / Azure / un modèle local se fait en fournissant une autre implémentation, sans
 * toucher aux appelants.
 */
public interface EmbeddingService {

    /**
     * Calcule le vecteur d'embedding d'un texte.
     *
     * @param text texte à encoder (non nul, non vide)
     * @return le vecteur dense
     */
    EmbeddingVector embed(String text);

    /**
     * Version batch — encode plusieurs textes en un seul appel (plus efficace pour l'ingestion).
     *
     * @param texts textes à encoder (ordre préservé dans le résultat)
     * @return un vecteur par texte, dans le même ordre
     */
    List<EmbeddingVector> embedAll(List<String> texts);

    /**
     * Dimensionnalité des vecteurs produits par le modèle courant (ex. 768 pour nomic-embed-text).
     * Utile pour valider la cohérence avec la colonne {@code vector(N)} du {@link VectorStore}.
     */
    int dimension();
}
