package com.onesley.oneclick.core.ai.api;

import java.util.Arrays;

/**
 * Vecteur d'embedding — objet valeur du domaine {@code core/ai}.
 *
 * <p>Encapsule un vecteur dense immuable (copie défensive à la construction et à la lecture) et porte
 * ses invariants (non nul, non vide). Neutre vis-à-vis de tout provider : ni LangChain4j, ni pgvector,
 * ni Ollama ne transparaissent ici. Les adaptateurs (embedding, vector store) convertissent entre ce
 * type et leurs représentations propres ({@code float[]}, littéral pgvector, {@code Embedding}…).
 */
public final class EmbeddingVector {

    private final float[] values;

    private EmbeddingVector(float[] values) {
        this.values = values;
    }

    /** Construit un vecteur à partir d'un tableau (copié défensivement). */
    public static EmbeddingVector of(float[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("EmbeddingVector : le tableau ne peut pas être nul ou vide");
        }
        return new EmbeddingVector(values.clone());
    }

    /** Dimensionnalité du vecteur (ex. 768). */
    public int dimension() {
        return values.length;
    }

    /** Copie du tableau sous-jacent (le vecteur reste immuable). */
    public float[] toArray() {
        return values.clone();
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof EmbeddingVector other) && Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "EmbeddingVector[dimension=" + values.length + "]"; // ne jamais logguer les valeurs
    }
}
