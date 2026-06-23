package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.NotificationCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.core.notification.api.NotificationDtos.*;
import com.onesley.oneclick.core.notification.api.NotificationDtos;
import com.onesley.oneclick.core.notification.api.NotificationDtos.CampaignCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.CampaignDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.DeviceTokenCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.DeviceTokenDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.MarkAllReadResultDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.UnreadCountDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service du microservice notification (Phase 2 §21 spec senior).
 *
 * <p>Pattern microservice : pas de référence aux entities {@code User}/{@code Tenant} de
 * oneclick-core. Toutes les FK sont matérialisées en {@code UUID} en colonnes directes.
 * Pas de {@code entityManager.getReference()}.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notifRepo;
    private final NotificationCampaignRepository campaignRepo;
    private final DeviceTokenRepository tokenRepo;
    private final ApplicationEventPublisher events;
    private final NotificationRecipientGuard recipientGuard; // garde-fou FK recipient_user_id → users

    // ─── Notifications ───────────────────────────────────────────────────────

    public Page<NotificationDto> findAll(UUID recipientUserId, Boolean unreadOnly, int page, int size) {
        Specification<Notification> spec = (root, q, cb) -> cb.conjunction();
        if (recipientUserId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("recipientUserId"), recipientUserId));
        }
        if (Boolean.TRUE.equals(unreadOnly)) {
            spec = spec.and((root, q, cb) -> cb.isNull(root.get("readAt")));
        }
        return notifRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(Notification::toDto);
    }

    @Transactional
    public NotificationDto create(NotificationCreateDto dto) {
        // Garde-fou FK : si le destinataire n'existe pas/plus dans users (ex: event Modulith dormant
        // rejoué après suppression du compte), on saute l'insert au lieu de violer la FK au COMMIT
        // (ce qui ferait échouer le listener async + laisserait la publication d'event incomplète →
        // rejouée en boucle à chaque restart). Retour null = notif non créée (cf appelants).
        if (!recipientGuard.exists(dto.recipientUserId())) {
            log.warn("[notification] destinataire {} absent de users — notification ignorée (type={})",
                dto.recipientUserId(), dto.type());
            return null;
        }
        String channel = dto.channel() != null ? dto.channel() : "inapp";
        Notification n = new Notification(UUID.randomUUID(), dto.recipientUserId(), dto.type(), channel,
            dto.title(), dto.body());
        if (dto.link() != null) n.setLink(dto.link());
        NotificationDto saved = notifRepo.save(n).toDto();
        // Temps réel (G) : signal STOMP poussé après commit au destinataire (cloche live, pas de polling).
        // Listener dans le package realtime (frontière Modulith : core.notification ne dépend pas de realtime).
        events.publishEvent(new NotificationCreatedEvent(
            saved.recipientUserId(), saved.id(), saved.type(), Instant.now()));
        return saved;
    }

    /**
     * Notif rappel de réservation (J-1 / H-2) — Sprint R1 (parité push legacy).
     *
     * <p>Écrit {@code metadata = {reservationId, slot}} : c'est exactement ce que le cron H-2
     * inspecte pour l'anti-doublon ({@code NOT EXISTS ... metadata->>'slot' = 'h2'}). Le
     * {@code channel='push'} reflète que ce rappel déclenche aussi un push (envoyé séparément
     * par {@code NotificationEventHandler}, frontière Modulith).
     *
     * <p>Package-private : appelé uniquement par l'event handler du même module.
     */
    @Transactional
    void createReservationReminder(UUID recipientUserId, String title, String body, String link,
                                   UUID reservationId, String slot) {
        if (!recipientGuard.exists(recipientUserId)) { log.warn("[notification] rappel résa ignoré — destinataire {} absent", recipientUserId); return; }
        Notification n = new Notification(UUID.randomUUID(), recipientUserId, "reservation", "push", title, body);
        if (link != null) n.setLink(link);
        n.getMetadata().put("reservationId", reservationId.toString());
        n.getMetadata().put("slot", slot);
        notifRepo.save(n);
        events.publishEvent(new NotificationCreatedEvent(
            recipientUserId, n.getId(), "reservation", Instant.now()));
    }

    /**
     * Rappel d'une réservation de RESSOURCE PCC (J-1 / H-2) — gap #4 (calque
     * {@link #createReservationReminder}). {@code metadata = {bookingId, slot}} : inspecté par
     * {@code ResourceBookingCronJobs} pour l'anti-doublon H-2 ({@code NOT EXISTS ... metadata->>'slot'='h2'}).
     * Type {@code reservation} (les bookings PCC partagent la cloche « réservations » côté client),
     * {@code channel='push'} (le push est envoyé séparément par {@code NotificationEventHandler}).
     * Package-private.
     */
    @Transactional
    void createResourceBookingReminder(UUID recipientUserId, String title, String body, String link,
                                       UUID bookingId, String slot) {
        if (!recipientGuard.exists(recipientUserId)) { log.warn("[notification] rappel booking ignoré — destinataire {} absent", recipientUserId); return; }
        Notification n = new Notification(UUID.randomUUID(), recipientUserId, "reservation", "push", title, body);
        if (link != null) n.setLink(link);
        n.getMetadata().put("bookingId", bookingId.toString());
        n.getMetadata().put("slot", slot);
        notifRepo.save(n);
        events.publishEvent(new NotificationCreatedEvent(
            recipientUserId, n.getId(), "reservation", Instant.now()));
    }

    /**
     * Alerte « points fidélité bientôt expirés » (J-30/J-15/J-7) — Feature A (parité legacy
     * {@code notify-expiring-points}, enrichie du push réel).
     *
     * <p>Écrit {@code metadata = {kind:'points_expiring', accountId, milestone}} : c'est exactement
     * ce que le cron {@code LoyaltyCronJobs.alertExpiringPoints} inspecte pour l'anti-doublon
     * ({@code NOT EXISTS ... metadata->>'milestone' = :milestone}). {@code channel='push'} reflète
     * que cette alerte déclenche aussi un push (envoyé séparément par {@code NotificationEventHandler}).
     *
     * <p>Package-private : appelé uniquement par l'event handler du même module.
     */
    @Transactional
    void createPointsExpiringAlert(UUID recipientUserId, String title, String body, String link,
                                   UUID accountId, String milestone) {
        if (!recipientGuard.exists(recipientUserId)) { log.warn("[notification] alerte points ignorée — destinataire {} absent", recipientUserId); return; }
        Notification n = new Notification(UUID.randomUUID(), recipientUserId, "loyalty", "push", title, body);
        if (link != null) n.setLink(link);
        n.getMetadata().put("kind", "points_expiring");
        n.getMetadata().put("accountId", accountId.toString());
        n.getMetadata().put("milestone", milestone);
        notifRepo.save(n);
        events.publishEvent(new NotificationCreatedEvent(
            recipientUserId, n.getId(), "loyalty", Instant.now()));
    }

    /**
     * Alerte « contrat partenaire bientôt expiré » (J-30/J-15/J-7) — Feature B (parité legacy
     * {@code notify-expiring-contracts}). Une notif par admin destinataire.
     *
     * <p>{@code metadata = {kind:'contract_expiring', contractId, milestone}} : anti-doublon du cron
     * {@code FinancialCronJobs.alertExpiringContracts} (1 alerte par contrat × jalon, quel que soit
     * le nombre d'admins). Type {@code system} (whitelisté). Package-private.
     */
    @Transactional
    void createContractExpiringAlert(UUID recipientUserId, String title, String body, String link,
                                     UUID contractId, String milestone) {
        if (!recipientGuard.exists(recipientUserId)) { log.warn("[notification] alerte contrat ignorée — destinataire {} absent", recipientUserId); return; }
        Notification n = new Notification(UUID.randomUUID(), recipientUserId, "system", "push", title, body);
        if (link != null) n.setLink(link);
        n.getMetadata().put("kind", "contract_expiring");
        n.getMetadata().put("contractId", contractId.toString());
        n.getMetadata().put("milestone", milestone);
        notifRepo.save(n);
        events.publishEvent(new NotificationCreatedEvent(
            recipientUserId, n.getId(), "system", Instant.now()));
    }

    /**
     * Alerte de TRANSPARENCE « pénalité no-show devenue définitive » (48h sans contestation) —
     * Lot B10. La pénalité de réputation a déjà été appliquée au marquage du no_show ; cette notif
     * informe seulement le client que le délai de contestation est expiré.
     *
     * <p>{@code metadata = {kind:'noshow_penalty_final', reservationId}} : c'est exactement ce que le
     * cron {@code ReservationCronJobs.finalizeNoShowPenalties} inspecte pour l'anti-doublon
     * ({@code NOT EXISTS ... metadata->>'kind' = 'noshow_penalty_final' AND metadata->>'reservationId' = ...}).
     * Type {@code reservation} (whitelisté). {@code channel='inapp'} (transparence, pas de push).
     * Package-private : appelé uniquement par l'event handler du même module.
     */
    @Transactional
    void createNoShowPenaltyFinalAlert(UUID recipientUserId, String title, String body, String link,
                                       UUID reservationId) {
        if (!recipientGuard.exists(recipientUserId)) { log.warn("[notification] alerte pénalité no-show ignorée — destinataire {} absent", recipientUserId); return; }
        Notification n = new Notification(UUID.randomUUID(), recipientUserId, "reservation", "inapp", title, body);
        if (link != null) n.setLink(link);
        n.getMetadata().put("kind", "noshow_penalty_final");
        n.getMetadata().put("reservationId", reservationId.toString());
        notifRepo.save(n);
        events.publishEvent(new NotificationCreatedEvent(
            recipientUserId, n.getId(), "reservation", Instant.now()));
    }

    @Transactional
    public NotificationDto markRead(UUID id) {
        Notification n = notifRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Notification", id));
        SecurityHelper.requireOwnerOrAdmin(n.getRecipientUserId());
        if (n.getReadAt() == null) n.markRead();
        return notifRepo.save(n).toDto();
    }

    /**
     * Cloche notification : liste complète des notifications d'un user, triée DESC.
     *
     * <p>Accès owner-only (ou admin). Pas de pagination — la cloche affiche
     * généralement les N dernières en mémoire. Pour de grands volumes, utiliser
     * {@link #findAll(UUID, Boolean, int, int)} avec pagination explicite.
     */
    public List<NotificationDto> findByUser(UUID userId, Boolean unreadOnly) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        List<Notification> notifs = Boolean.TRUE.equals(unreadOnly)
            ? notifRepo.findAllByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId)
            : notifRepo.findAllByRecipientUserIdOrderByCreatedAtDesc(userId);
        return notifs.stream().map(Notification::toDto).toList();
    }

    /** Badge cloche : compteur de non lues pour un user. */
    public UnreadCountDto unreadCountByUser(UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return new UnreadCountDto(notifRepo.countByRecipientUserIdAndReadAtIsNull(userId));
    }

    /** Action "Tout lire" depuis la cloche : marque toutes les non lues comme lues. */
    @Transactional
    public MarkAllReadResultDto markAllReadByUser(UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        int updated = notifRepo.markAllReadByRecipientUserId(userId, Instant.now());
        return new MarkAllReadResultDto(updated);
    }

    // ─── Campaigns ───────────────────────────────────────────────────────────

    public List<CampaignDto> findCampaignsByTenant(UUID tenantId) {
        return campaignRepo.findAllByTenantId(tenantId).stream().map(NotificationCampaign::toDto).toList();
    }

    @Transactional
    public CampaignDto createCampaign(CampaignCreateDto dto) {
        NotificationCampaign c = new NotificationCampaign(UUID.randomUUID(), dto.tenantId(), dto.title(), dto.message());
        if (dto.targetSegment() != null) c.setTargetSegment(dto.targetSegment());
        if (dto.scheduledAt() != null) {
            c.setScheduledAt(dto.scheduledAt());
            c.setStatus("scheduled");
        }
        return campaignRepo.save(c).toDto();
    }

    // ─── Device tokens ───────────────────────────────────────────────────────

    public List<DeviceTokenDto> findTokensByUser(UUID userId) {
        return tokenRepo.findAllByUserId(userId).stream().map(DeviceToken::toDto).toList();
    }

    @Transactional
    public DeviceTokenDto registerToken(DeviceTokenCreateDto dto) {
        return tokenRepo.findByToken(dto.token())
            .map(DeviceToken::toDto)
            .orElseGet(() -> {
                DeviceToken t = new DeviceToken(UUID.randomUUID(), dto.userId(), dto.token(), dto.platform());
                if (dto.appId() != null) t.setAppId(dto.appId());
                return tokenRepo.save(t).toDto();
            });
    }

    @Transactional
    public void unregisterToken(UUID id) {
        DeviceToken t = tokenRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("DeviceToken", id));
        SecurityHelper.requireOwnerOrAdmin(t.getUserId());
        tokenRepo.delete(t);
    }
}
