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
public class NotificationService {

    private final NotificationRepository notifRepo;
    private final NotificationCampaignRepository campaignRepo;
    private final DeviceTokenRepository tokenRepo;
    private final ApplicationEventPublisher events;

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
