package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.PromoNotificationDtos.*;
import com.onesley.oneclick.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class PromoNotificationService {

    private final PromoNotificationRequestRepository requestRepo;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public List<PromoRequestDto> findAll(String status, UUID restaurantId) {
        if (status != null) return requestRepo.findByStatus(status).stream().map(PromoRequestDto::from).toList();
        if (restaurantId != null) return requestRepo.findByRestaurant(restaurantId).stream().map(PromoRequestDto::from).toList();
        return requestRepo.findAllActive().stream().map(PromoRequestDto::from).toList();
    }

    @Transactional(readOnly = true)
    public PromoRequestDto findById(UUID id) {
        return PromoRequestDto.from(requestRepo.findById(id).orElseThrow(() -> new NotFoundException("PromoNotificationRequest", id)));
    }

    public PromoRequestDto create(PromoRequestCreateDto dto) {
        PromoNotificationRequest p = new PromoNotificationRequest();
        p.setTenantId(dto.tenantId());
        p.setRestaurantId(dto.restaurantId());
        p.setOfferId(dto.offerId());
        p.setTitle(dto.title());
        p.setBody(dto.body());
        p.setSegment(dto.segment() == null ? "all" : dto.segment());
        p.setRequestedBy(dto.requestedBy());
        return PromoRequestDto.from(requestRepo.save(p));
    }

    public PromoRequestDto review(UUID id, PromoRequestReviewDto dto) {
        PromoNotificationRequest p = requestRepo.findById(id).orElseThrow(() -> new NotFoundException("PromoNotificationRequest", id));
        p.setStatus(dto.status());
        p.setReviewedBy(dto.reviewedBy());
        p.setReviewedAt(Instant.now());
        p.setRejectionReason(dto.rejectionReason());
        return PromoRequestDto.from(requestRepo.save(p));
    }

    public PromoRequestDto markSent(UUID id, int sentCount, String error) {
        PromoNotificationRequest p = requestRepo.findById(id).orElseThrow(() -> new NotFoundException("PromoNotificationRequest", id));
        p.setStatus("sent");
        p.setPushSentAt(Instant.now());
        p.setPushSentCount(sentCount);
        p.setPushError(error);
        return PromoRequestDto.from(requestRepo.save(p));
    }

    @Transactional(readOnly = true)
    public PromoStatsDto stats() {
        Object[] row = (Object[]) em.createNativeQuery("""
            SELECT
              (SELECT COUNT(*) FROM offers WHERE deleted_at IS NULL),
              (SELECT COUNT(*) FROM offers WHERE deleted_at IS NULL AND enabled = true AND (expires_at IS NULL OR expires_at > NOW())),
              (SELECT COUNT(*) FROM offer_impressions WHERE created_at > NOW() - INTERVAL '30 days'),
              (SELECT COUNT(*) FROM redemptions WHERE created_at > NOW() - INTERVAL '30 days'),
              (SELECT COALESCE(SUM(push_sent_count), 0) FROM promo_notification_requests WHERE push_sent_at > NOW() - INTERVAL '30 days')
            """).getSingleResult();
        return new PromoStatsDto(
            ((Number) row[0]).longValue(),
            ((Number) row[1]).longValue(),
            ((Number) row[2]).longValue(),
            ((Number) row[3]).longValue(),
            ((Number) row[4]).longValue()
        );
    }
}
