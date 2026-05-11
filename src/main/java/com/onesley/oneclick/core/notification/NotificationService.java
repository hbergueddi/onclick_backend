package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.core.notification.NotificationDtos.*;

/**
 * Service du microservice notification (Phase 2 §21 spec senior).
 *
 * <p>Pattern microservice : pas de référence aux entities {@code User}/{@code Tenant} de
 * oneclick-core. Toutes les FK sont matérialisées en {@code UUID} en colonnes directes.
 * Pas de {@code entityManager.getReference()}.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notifRepo;
    private final NotificationCampaignRepository campaignRepo;
    private final DeviceTokenRepository tokenRepo;

    public NotificationService(NotificationRepository notifRepo,
                               NotificationCampaignRepository campaignRepo,
                               DeviceTokenRepository tokenRepo) {
        this.notifRepo = notifRepo;
        this.campaignRepo = campaignRepo;
        this.tokenRepo = tokenRepo;
    }

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
            .map(NotificationDto::from);
    }

    @Transactional
    public NotificationDto create(NotificationCreateDto dto) {
        String channel = dto.channel() != null ? dto.channel() : "inapp";
        Notification n = new Notification(UUID.randomUUID(), dto.recipientUserId(), dto.type(), channel,
            dto.title(), dto.body());
        if (dto.link() != null) n.setLink(dto.link());
        return NotificationDto.from(notifRepo.save(n));
    }

    @Transactional
    public NotificationDto markRead(UUID id) {
        Notification n = notifRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Notification", id));
        if (n.getReadAt() == null) n.markRead();
        return NotificationDto.from(notifRepo.save(n));
    }

    // ─── Campaigns ───────────────────────────────────────────────────────────

    public List<CampaignDto> findCampaignsByTenant(UUID tenantId) {
        return campaignRepo.findAllByTenantId(tenantId).stream().map(CampaignDto::from).toList();
    }

    @Transactional
    public CampaignDto createCampaign(CampaignCreateDto dto) {
        NotificationCampaign c = new NotificationCampaign(UUID.randomUUID(), dto.tenantId(), dto.title(), dto.message());
        if (dto.targetSegment() != null) c.setTargetSegment(dto.targetSegment());
        if (dto.scheduledAt() != null) {
            c.setScheduledAt(dto.scheduledAt());
            c.setStatus("scheduled");
        }
        return CampaignDto.from(campaignRepo.save(c));
    }

    // ─── Device tokens ───────────────────────────────────────────────────────

    public List<DeviceTokenDto> findTokensByUser(UUID userId) {
        return tokenRepo.findAllByUserId(userId).stream().map(DeviceTokenDto::from).toList();
    }

    @Transactional
    public DeviceTokenDto registerToken(DeviceTokenCreateDto dto) {
        return tokenRepo.findByToken(dto.token())
            .map(DeviceTokenDto::from)
            .orElseGet(() -> {
                DeviceToken t = new DeviceToken(UUID.randomUUID(), dto.userId(), dto.token(), dto.platform());
                if (dto.appId() != null) t.setAppId(dto.appId());
                return DeviceTokenDto.from(tokenRepo.save(t));
            });
    }

    @Transactional
    public void unregisterToken(UUID id) {
        DeviceToken t = tokenRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("DeviceToken", id));
        tokenRepo.delete(t);
    }
}
