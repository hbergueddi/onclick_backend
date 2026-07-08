package com.onesley.oneclick.core.ai.internal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Façade des métriques Micrometer du module {@code core/ai} — observabilité production.
 *
 * <p>Centralise la définition des compteurs/timers (noms, descriptions) pour que les implémentations
 * (embedding, vector store, futur pipeline RAG) restent lisibles et cohérentes. <b>Aucun type
 * Micrometer ne fuit dans les ports {@code api}</b> : l'observabilité vit exclusivement côté
 * {@code internal}, le domaine reste neutre.
 *
 * <p>Métriques exposées (préfixe {@code ai.}) :
 * <ul>
 *   <li>{@code ai.embedding.duration} (timer) — durée d'un embedding (tag {@code mode}=single|batch)</li>
 *   <li>{@code ai.embedding.errors} (counter) — échecs d'embedding</li>
 *   <li>{@code ai.vectorstore.add.duration} (timer) — durée d'une insertion vectorielle</li>
 *   <li>{@code ai.vectorstore.search.duration} (timer) — durée d'une recherche par similarité</li>
 *   <li>{@code ai.vectorstore.errors} (counter) — échecs du vector store</li>
 *   <li>{@code ai.rag.query.duration} (timer) — durée d'une requête RAG de bout en bout (pipeline à venir)</li>
 * </ul>
 */
@Component
class AiMetrics {

    private final MeterRegistry registry;

    private final Timer embedSingle;
    private final Timer embedBatch;
    private final Counter embedErrors;
    private final Timer vectorAdd;
    private final Timer vectorSearch;
    private final Counter vectorErrors;
    private final Timer ragQuery;

    AiMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.embedSingle = Timer.builder("ai.embedding.duration")
            .description("Durée d'un embedding").tag("mode", "single").register(registry);
        this.embedBatch = Timer.builder("ai.embedding.duration")
            .description("Durée d'un embedding").tag("mode", "batch").register(registry);
        this.embedErrors = Counter.builder("ai.embedding.errors")
            .description("Échecs d'embedding").register(registry);
        this.vectorAdd = Timer.builder("ai.vectorstore.add.duration")
            .description("Durée d'une insertion vectorielle").register(registry);
        this.vectorSearch = Timer.builder("ai.vectorstore.search.duration")
            .description("Durée d'une recherche par similarité").register(registry);
        this.vectorErrors = Counter.builder("ai.vectorstore.errors")
            .description("Échecs du vector store").register(registry);
        this.ragQuery = Timer.builder("ai.rag.query.duration")
            .description("Durée d'une requête RAG de bout en bout").register(registry);
    }

    <T> T recordEmbedSingle(Supplier<T> op) {
        return record(embedSingle, embedErrors, op);
    }

    <T> T recordEmbedBatch(Supplier<T> op) {
        return record(embedBatch, embedErrors, op);
    }

    <T> T recordVectorAdd(Supplier<T> op) {
        return record(vectorAdd, vectorErrors, op);
    }

    <T> T recordVectorSearch(Supplier<T> op) {
        return record(vectorSearch, vectorErrors, op);
    }

    /**
     * Timer de la requête RAG complète (embed → search → prompt → chat). Exposé pour le futur pipeline ;
     * utiliser {@code ragQueryTimer().record(...)} une fois le pipeline implémenté.
     */
    Timer ragQueryTimer() {
        return ragQuery;
    }

    /** Chronomètre l'opération ; incrémente le compteur d'erreurs si elle échoue, puis relance. */
    private <T> T record(Timer timer, Counter errorCounter, Supplier<T> op) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return op.get();
        } catch (RuntimeException e) {
            errorCounter.increment();
            throw e;
        } finally {
            sample.stop(timer);
        }
    }
}
