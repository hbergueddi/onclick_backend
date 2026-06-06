package com.onesley.oneclick.modules.restaurant.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs des horaires d'ouverture (table normalisée {@code business_hours}, §4).
 *
 * <p>Remplace le {@code opening_hours} jsonb legacy. Convention
 * {@code dayOfWeek} : 0 = dimanche … 6 = samedi (cohérent avec l'entité
 * {@code BusinessHour} et la contrainte CHECK 0-6 en base).
 *
 * <p>Un jour fermé = absence de ligne (pas de flag {@code closed}). Le
 * remplacement complet (PUT) reçoit donc uniquement les jours ouverts.
 */
public final class BusinessHourDtos {

    private BusinessHourDtos() {}

    /** Un créneau horaire exposé (lecture). */
    public record BusinessHourDto(UUID id, int dayOfWeek, LocalTime startTime, LocalTime endTime) {}

    /** Une entrée d'écriture (PUT) — id généré côté serveur. */
    public record BusinessHourEntryDto(
        @Min(0) @Max(6) int dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
    ) {}

    /** Remplacement complet de la semaine. Liste vide = établissement fermé tous les jours. */
    public record BusinessHoursPutDto(
        @NotNull @Valid List<BusinessHourEntryDto> hours
    ) {}
}
