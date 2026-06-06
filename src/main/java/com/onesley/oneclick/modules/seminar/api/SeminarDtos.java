package com.onesley.oneclick.modules.seminar.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTOs publics « Séminaires » (PCC) exposés par {@code SeminarController}.
 *
 * <p>Records immutables + Bean Validation sur les payloads d'entrée. Pas de logique de mapping
 * ici : la conversion Entity → {@link SeminarRequestDto} se fait dans {@code PccSeminarService}.</p>
 */
public final class SeminarDtos {

    private SeminarDtos() {}

    /** Statuts valides du workflow séminaire (regex Bean Validation + garde-fou service). */
    public static final String STATUS_REGEX =
        "^(demandee|en_traitement|devis_envoye|confirmee|refusee|annulee)$";

    /**
     * Payload de soumission d'une demande de séminaire (membre). {@code contactPhone},
     * {@code preferredDate*} et {@code needsText} sont optionnels ; {@code expectedAttendees} ≥ 1.
     */
    public record CreateSeminarRequestDto(
        @NotBlank @Size(max = 255) String companyName,
        @NotBlank @Size(max = 255) String contactName,
        @NotBlank @Email @Size(max = 320) String contactEmail,
        @Size(max = 40) String contactPhone,
        @NotNull @Min(1) Integer expectedAttendees,
        LocalDate preferredDateStart,
        LocalDate preferredDateEnd,
        @Size(max = 4000) String needsText
    ) {}

    /**
     * Payload de changement de statut (commercial). {@code status} ∈ workflow ;
     * {@code notesInternal} optionnel (≤ 4000) — non fourni = inchangé.
     */
    public record UpdateSeminarStatusDto(
        @NotBlank @Pattern(regexp = STATUS_REGEX,
            message = "status invalide (demandee|en_traitement|devis_envoye|confirmee|refusee|annulee)")
        String status,
        @Size(max = 4000) String notesInternal
    ) {}

    /**
     * Une demande de séminaire (payload REST + WebSocket STOMP).
     *
     * <p>Enrichi du nom/contact de l'organisateur (read-view UserDirectoryApi) pour l'affichage de
     * l'inbox commercial. {@code notesInternal} n'est renseigné que pour les lectures staff/admin
     * (jamais exposé au membre — cf. {@code PccSeminarService}).</p>
     */
    public record SeminarRequestDto(
        UUID id,
        UUID organizerId,
        String organizerFirstName,
        String organizerLastName,
        String organizerEmail,
        UUID tenantId,
        String companyName,
        String contactName,
        String contactEmail,
        String contactPhone,
        int expectedAttendees,
        LocalDate preferredDateStart,
        LocalDate preferredDateEnd,
        String needsText,
        String status,
        String notesInternal,
        Instant createdAt,
        Instant updatedAt
    ) {}
}
