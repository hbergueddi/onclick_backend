package com.onesley.oneclick.entity.restaurant;

/**
 * Créneau horaire d'ouverture — un élément du tableau JSONB {@code opening_hours}.
 *
 * <p>Format DB ({@code jsonb}) : {@code [{"day":0,"open":"12:00","close":"21:00"}, ...]}.
 * Convention : {@code day} = 0 (dimanche) à 6 (samedi), {@code open}/{@code close} en
 * "HH:MM" (string, pas {@code java.time.LocalTime} — préservation exacte du format
 * utilisé en frontend React).
 *
 * <p>Mappé via Jackson par défaut (les records Java 26 ont un constructor canonique
 * automatiquement utilisable).
 */
public record OpeningHourSlot(
    int day,
    String open,
    String close
) {
}
