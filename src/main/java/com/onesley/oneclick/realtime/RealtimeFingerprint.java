package com.onesley.oneclick.realtime;

import jakarta.persistence.EntityManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Bug 37 — Empreinte légère d'un domaine pour la détection de changement.
 *
 * <p>Pour les dashboards dont le backend n'a pas (encore) de service d'agrégat
 * dédié, on pousse une simple empreinte {@code (rowCount, lastChange)} calculée
 * par {@code SELECT count(*), max(updated_at)}. Quand elle change (insert OU
 * update), le publisher pousse → le front invalide sa query → re-fetch REST.
 *
 * <p>Coût : 1 requête scalaire indexée par intervalle (pas de recalcul d'agrégat
 * coûteux). Record → {@code equals} par valeur pour la détection de changement.
 */
public record RealtimeFingerprint(long rowCount, Instant lastChange) {

    /**
     * Calcule l'empreinte d'une table via {@code count(*) + max(updated_at)}.
     *
     * @param table nom de table — CONSTANTE en dur côté publisher (jamais d'input
     *              utilisateur → pas d'injection SQL).
     */
    public static RealtimeFingerprint of(EntityManager em, String table) {
        return of(em, table, "updated_at");
    }

    /**
     * Variante avec colonne (ou expression) de changement explicite, pour les tables
     * <strong>sans {@code updated_at}</strong> : append-only ({@code created_at}) ou
     * avec horodatage d'action ({@code greatest(created_at, acknowledged_at)} pour
     * capter à la fois l'insertion et l'acquittement).
     *
     * @param table        nom de table — CONSTANTE en dur côté publisher.
     * @param changeColumn colonne/expression SQL de détection de changement —
     *                     CONSTANTE en dur côté publisher (jamais d'input utilisateur
     *                     → pas d'injection SQL).
     */
    public static RealtimeFingerprint of(EntityManager em, String table, String changeColumn) {
        Object[] row = (Object[]) em.createNativeQuery(
            "SELECT count(*), max(" + changeColumn + ") FROM " + table).getSingleResult();
        long count = ((Number) row[0]).longValue();
        return new RealtimeFingerprint(count, toInstant(row[1]));
    }

    private static Instant toInstant(Object value) {
        return switch (value) {
            case null -> null;
            case Instant i -> i;
            case OffsetDateTime odt -> odt.toInstant();
            case Timestamp t -> t.toInstant();
            default -> null;
        };
    }
}
