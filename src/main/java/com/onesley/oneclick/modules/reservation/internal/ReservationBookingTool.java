package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.core.ai.api.AiTool;
import com.onesley.oneclick.modules.reservation.api.ReservationCreateDto;
import com.onesley.oneclick.modules.reservation.api.ReservationDto;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.security.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

/**
 * Outil métier <b>écriture</b> exposé au chatbot : créer une réservation pour le client connecté.
 *
 * <p>Vit dans {@code modules/reservation} (propriétaire de la donnée) et implémente {@link AiTool}.
 *
 * <h3>Sécurité (règles non négociables)</h3>
 * <ul>
 *   <li><b>Client = utilisateur connecté</b> ({@link SecurityHelper#currentUserId()}) — jamais du LLM.</li>
 *   <li><b>Tenant résolu côté serveur</b> depuis le {@code restaurantId}.</li>
 *   <li><b>Contrôle de périmètre</b> ({@link TenantScope#canSeeTenant(UUID)}).</li>
 *   <li><b>Confirmation avant écriture</b> : sans {@code confirm=true}, l'outil ne crée rien et renvoie
 *       un récapitulatif à faire valider par l'utilisateur.</li>
 * </ul>
 *
 * <h3>Fuseau horaire</h3>
 * <p>Les heures sont interprétées en <b>heure locale du Maroc</b> ({@code Africa/Casablanca}) : on prend
 * l'heure « murale » fournie et on ignore tout offset émis par le LLM (peu fiable). « 20h » = 20h Maroc.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationBookingTool implements AiTool {

    private static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm").withZone(ZONE);

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final TenantScope tenantScope;

    @Override
    public String name() {
        return "book_reservation";
    }

    @Override
    public String description() {
        return "Crée une réservation pour le client connecté. Déroulé en DEUX temps : "
            + "1) appelle d'abord SANS confirm (ou confirm=false) pour obtenir un récapitulatif ; "
            + "2) montre le récapitulatif à l'utilisateur et n'appelle avec confirm=true qu'APRÈS son accord explicite. "
            + "restaurant_id vient de search_restaurants ; les heures sont en HEURE LOCALE (Maroc).";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of(
            "restaurant_id", "Identifiant (UUID) du restaurant, issu de search_restaurants",
            "reservation_at", "Date et heure en HEURE LOCALE Maroc, ISO-8601 (ex. 2026-07-20T20:30:00)",
            "guest_count", "Nombre de personnes (entier positif)",
            "confirm", "true UNIQUEMENT après accord explicite de l'utilisateur ; sinon renvoie un récapitulatif");
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        UUID clientId = SecurityHelper.currentUserId();
        if (clientId == null) {
            return "Aucun utilisateur authentifié : impossible de réserver.";
        }

        UUID restaurantId;
        try {
            restaurantId = UUID.fromString(String.valueOf(arguments.get("restaurant_id")).trim());
        } catch (RuntimeException e) {
            return "Identifiant de restaurant invalide. Utilise d'abord search_restaurants.";
        }

        Instant reservationAt;
        try {
            reservationAt = parseLocalMorocco(String.valueOf(arguments.get("reservation_at")).trim());
        } catch (RuntimeException e) {
            return "Date invalide : fournis la date-heure (heure locale Maroc), ex. 2026-07-20T20:30:00.";
        }

        int guestCount;
        try {
            Object g = arguments.get("guest_count");
            guestCount = (g instanceof Number n) ? n.intValue() : Integer.parseInt(String.valueOf(g).trim());
        } catch (RuntimeException e) {
            return "Nombre de personnes invalide.";
        }
        if (guestCount < 1) {
            return "Le nombre de personnes doit être au moins 1.";
        }

        UUID tenantId = reservationRepository.findTenantIdByRestaurantId(restaurantId).orElse(null);
        if (tenantId == null) {
            return "Restaurant introuvable.";
        }
        if (!tenantScope.canSeeTenant(tenantId)) {
            return "Ce restaurant n'est pas accessible depuis votre compte.";
        }
        String restaurantName = reservationRepository.findRestaurantNameById(restaurantId).orElse("le restaurant");

        // Garde de confirmation : sans confirm=true, on NE crée PAS — on renvoie un récapitulatif.
        if (!isConfirmed(arguments.get("confirm"))) {
            return "À confirmer : réservation chez " + restaurantName + " le " + FMT.format(reservationAt)
                + " pour " + guestCount + " personne(s). Demande à l'utilisateur de confirmer, puis rappelle "
                + "book_reservation avec confirm=true.";
        }

        // clientId = user connecté, tenantId résolu serveur — jamais fournis par le LLM.
        ReservationDto r = reservationService.create(new ReservationCreateDto(
            tenantId, clientId, restaurantId, null, null, reservationAt, guestCount, null));

        log.info("[core/ai] book_reservation ok — reservation={} restaurant={} client={}",
            r.id(), restaurantId, clientId);
        return "Réservation enregistrée (statut : " + r.status() + ") chez " + restaurantName + " pour "
            + guestCount + " personne(s), le " + FMT.format(reservationAt) + ". Référence : " + r.id() + ".";
    }

    private static boolean isConfirmed(Object confirm) {
        return (confirm instanceof Boolean b) ? b : Boolean.parseBoolean(String.valueOf(confirm));
    }

    /**
     * Interprète la date-heure en <b>heure locale Maroc</b>. On réduit toujours à l'heure « murale »
     * (en ignorant un éventuel offset émis par le LLM), puis on applique {@code Africa/Casablanca}.
     */
    private static Instant parseLocalMorocco(String s) {
        LocalDateTime local;
        try {
            local = OffsetDateTime.parse(s).toLocalDateTime(); // ignore l'offset (ex. ...Z, +02:00)
        } catch (DateTimeParseException ignored) {
            local = LocalDateTime.parse(s);                    // pas de zone → tel quel
        }
        return local.atZone(ZONE).toInstant();
    }
}
