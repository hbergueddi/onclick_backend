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
 *   <li><b>Client = utilisateur connecté</b> ({@link SecurityHelper#currentUserId()}) — le LLM ne
 *       fournit jamais de {@code clientId} : impossible de réserver au nom d'autrui.</li>
 *   <li><b>Tenant résolu côté serveur</b> depuis le {@code restaurantId} (jamais fourni par le LLM).</li>
 *   <li><b>Contrôle de périmètre</b> : refus si le restaurant appartient à un tenant hors du périmètre
 *       visible du client ({@link TenantScope#canSeeTenant(UUID)}).</li>
 * </ul>
 *
 * <p>NB : la confirmation humaine définitive relève de l'UI ; la description invite le modèle à
 * confirmer les détails avec l'utilisateur avant d'appeler l'outil.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationBookingTool implements AiTool {

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm").withZone(ZoneId.of("Africa/Casablanca"));

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final TenantScope tenantScope;

    @Override
    public String name() {
        return "book_reservation";
    }

    @Override
    public String description() {
        return "Crée une réservation pour le client connecté. Confirme d'abord les détails avec "
            + "l'utilisateur. Nécessite l'id du restaurant (obtenu via search_restaurants), la date-heure "
            + "au format ISO-8601 (ex. 2026-07-20T20:30:00Z) et le nombre de personnes.";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of(
            "restaurant_id", "Identifiant (UUID) du restaurant, issu de search_restaurants",
            "reservation_at", "Date et heure au format ISO-8601 (ex. 2026-07-20T20:30:00Z)",
            "guest_count", "Nombre de personnes (entier positif)");
    }

    /**
     * Parse une date-heure ISO-8601 tolérante aux formats émis par les LLM :
     * offset ({@code ...+02:00} / {@code ...Z}) puis, à défaut, date-heure locale (zone Casablanca).
     */
    private static Instant parseFlexibleInstant(String s) {
        try {
            return OffsetDateTime.parse(s).toInstant();           // 2026-07-20T20:30:00+02:00 ou ...Z
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(s)                          // 2026-07-20T20:30:00 (sans zone)
                .atZone(ZoneId.of("Africa/Casablanca")).toInstant();
        }
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
            reservationAt = parseFlexibleInstant(String.valueOf(arguments.get("reservation_at")).trim());
        } catch (RuntimeException e) {
            return "Date invalide : fournis la date-heure au format ISO-8601 (ex. 2026-07-20T20:30:00Z).";
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

        // clientId = user connecté, tenantId résolu serveur — jamais fournis par le LLM.
        ReservationDto r = reservationService.create(new ReservationCreateDto(
            tenantId, clientId, restaurantId, null, null, reservationAt, guestCount, null));

        log.info("[core/ai] book_reservation ok — reservation={} restaurant={} client={}",
            r.id(), restaurantId, clientId);
        return "Réservation enregistrée (statut : " + r.status() + ") pour "
            + guestCount + " personne(s), le " + FMT.format(reservationAt)
            + ". Référence : " + r.id() + ".";
    }
}
