package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.core.ai.api.AiTool;
import com.onesley.oneclick.modules.reservation.api.ReservationDto;
import com.onesley.oneclick.security.SecurityHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outil métier <b>lecture seule</b> exposé au chatbot : les réservations du client connecté.
 *
 * <p>Vit dans le module {@code modules/reservation} (propriétaire de la donnée) et implémente le port
 * {@link AiTool} de {@code core.ai.api} — l'orchestrateur le découvre via Spring, sans que {@code core/ai}
 * ne dépende de la réservation. <b>Sécurité</b> : l'outil agit toujours au nom de l'utilisateur
 * authentifié ({@link SecurityHelper#currentUserId()}) ; il n'accepte aucun {@code clientId} en argument,
 * donc impossible de lire les réservations d'autrui via le LLM.
 */
@Component
@RequiredArgsConstructor
public class ReservationLookupTool implements AiTool {

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm").withZone(ZoneId.of("Africa/Casablanca"));
    private static final int MAX_RESULTS = 10;

    private final ReservationService reservationService;

    @Override
    public String name() {
        return "get_my_reservations";
    }

    @Override
    public String description() {
        return "Récupère les réservations du client actuellement connecté (les plus récentes en premier). "
            + "À utiliser quand l'utilisateur demande ses réservations, sa prochaine réservation, etc.";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of(); // aucun paramètre : l'outil lit toujours les réservations de l'utilisateur connecté
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        UUID userId = SecurityHelper.currentUserId();
        if (userId == null) {
            return "Aucun utilisateur authentifié : impossible de récupérer les réservations.";
        }
        List<ReservationDto> reservations = reservationService
            .findAll(userId, null, null, null, null, 0, MAX_RESULTS)
            .getContent();
        if (reservations.isEmpty()) {
            return "Vous n'avez aucune réservation.";
        }
        StringBuilder sb = new StringBuilder("Réservations du client (" + reservations.size() + ") :\n");
        for (ReservationDto r : reservations) {
            sb.append("- ")
                .append(r.restaurantName() != null ? r.restaurantName() : "restaurant")
                .append(r.restaurantCity() != null ? " (" + r.restaurantCity() + ")" : "")
                .append(r.reservationAt() != null ? " le " + FMT.format(r.reservationAt()) : "")
                .append(r.guestCount() != null ? " pour " + r.guestCount() + " pers." : "")
                .append(" — statut : ").append(r.status() != null ? r.status() : "inconnu")
                .append('\n');
        }
        return sb.toString();
    }
}
