package com.onesley.oneclick.shared;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Coercion d'une valeur temporelle issue d'une requête SQL native ({@code Object})
 * vers {@link Instant}.
 *
 * <p>Selon le driver / le mapping JPA, une colonne {@code timestamptz} peut revenir
 * en {@link Timestamp}, {@link OffsetDateTime}, {@link Instant}, ou (fallback) en
 * {@code String} ISO-8601. Cet helper mutualise la conversion jusqu'ici dupliquée
 * à l'identique dans {@code AdminViewsService}, {@code EnrollmentService} et
 * {@code LoyaltyExtensionService}.
 */
public final class Temporals {

    private Temporals() {}

    /** {@code null}-safe : retourne {@code null} si {@code o} est {@code null}. */
    public static Instant toInstant(Object o) {
        if (o == null) return null;
        if (o instanceof Instant i) return i;
        if (o instanceof Timestamp ts) return ts.toInstant();
        if (o instanceof OffsetDateTime odt) return odt.toInstant();
        return Instant.parse(o.toString());
    }
}
