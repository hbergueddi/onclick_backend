package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.exception.UnauthorizedException;
import com.onesley.oneclick.security.SecurityHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import com.onesley.oneclick.core.notification.api.NotificationDtos.StaffNotificationPrefsDto;
import lombok.RequiredArgsConstructor;

/**
 * Service des préférences de notifications staff (Gap #5 — port legacy B.5).
 *
 * <p>Self-service strict : toutes les opérations sont scopées sur le user courant
 * ({@link SecurityHelper#currentUserId()}). Aucun paramètre {@code userId} exposé —
 * impossible de lire/écrire les préférences d'un autre user, même pour un admin
 * (ce sont des préférences personnelles, pas des données administrables).
 *
 * <p>Pattern microservice (cohérent avec {@link NotificationService}) : pas de
 * référence aux entities {@code User}/{@code Tenant} ; FK matérialisée en UUID.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StaffNotificationPreferenceService {

    private final StaffNotificationPreferenceRepository repository;

    /**
     * Préférences du user courant. Renvoie les défauts (tous {@code true}) si aucune
     * row n'existe encore — la persistance se fait au 1er {@link #updateMine}.
     */
    public StaffNotificationPrefsDto getMine() {
        UUID userId = requireCurrentUser();
        return repository.findByUserId(userId)
            .map(StaffNotificationPreference::toDto)
            .orElseGet(StaffNotificationPrefsDto::allEnabled);
    }

    /**
     * P1pref — le toggle {@code category} d'un STAFF donné est-il activé ? Appelé par
     * {@code NotificationEventHandler} avant de notifier un staff (in-app ET push) : si le toggle
     * de la catégorie de la notif est OFF → on saute ce destinataire.
     *
     * <p><b>Défaut = ON</b> : un staff qui n'a jamais ouvert la page « Préférences » n'a pas de row
     * persistée — on considère alors TOUTES les catégories activées (opt-out, cohérent avec
     * {@link StaffNotificationPrefsDto#allEnabled()} et la création paresseuse au 1er {@link
     * #updateMine}). Idem si {@code staffId}/{@code category} sont {@code null} (garde défensive) ou
     * si la catégorie est inconnue (on ne filtre jamais à tort).
     *
     * <p>Contrairement à {@link #getMine}, ce lookup N'EST PAS self-service : il lit les préférences
     * d'un destinataire arbitraire. C'est légitime car (1) l'appelant est le moteur de notification
     * server-side (pas un endpoint exposé), (2) on ne renvoie qu'un booléen (pas de fuite de donnée
     * personnelle), (3) il vit dans le MÊME module {@code core.notification}.
     *
     * @param category l'une des 5 catégories : {@code booking}, {@code reservation}, {@code feedback},
     *                 {@code loyalty}, {@code system}
     */
    public boolean isStaffCategoryEnabled(UUID staffId, String category) {
        if (staffId == null || category == null) return true; // garde → ne jamais filtrer à tort
        return repository.findByUserId(staffId)
            .map(p -> switch (category) {
                case "booking"     -> p.isBooking();
                case "reservation" -> p.isReservation();
                case "feedback"    -> p.isFeedback();
                case "loyalty"     -> p.isLoyalty();
                case "system"      -> p.isSystem();
                default            -> true; // catégorie inconnue → on ne filtre pas
            })
            .orElse(true); // pas de préférence enregistrée → défaut ON (opt-out)
    }

    /** Upsert des 5 toggles pour le user courant (crée la row si absente). */
    @Transactional
    public StaffNotificationPrefsDto updateMine(StaffNotificationPrefsDto dto) {
        UUID userId = requireCurrentUser();
        StaffNotificationPreference pref = repository.findByUserId(userId)
            .orElseGet(() -> new StaffNotificationPreference(UUID.randomUUID(), userId));
        pref.apply(dto);
        return repository.save(pref).toDto();
    }

    /** Garde défensive : ne devrait jamais déclencher derrière {@code @PreAuthorize}. */
    private UUID requireCurrentUser() {
        UUID userId = SecurityHelper.currentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Authentification requise");
        }
        return userId;
    }
}
