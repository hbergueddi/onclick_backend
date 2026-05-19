package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Entity
@Table(name = "promo_notification_requests")
@Getter
public class PromoNotificationRequest extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id")
    @Setter private UUID tenantId;

    @Column(name = "restaurant_id")
    @Setter private UUID restaurantId;

    @Column(name = "offer_id")
    @Setter private UUID offerId;

    @Column(nullable = false, length = 255)
    @Setter @Size(max = 255) @NotBlank private String title;

    @Column @Setter private String body;

    @Column(nullable = false, length = 64)
    @Setter @Size(max = 64) @NotBlank private String segment = "all";

    @Column(nullable = false, length = 32)
    @Setter @Size(max = 32) @NotBlank @Pattern(regexp = "^(pending|approved|rejected|sent)$") private String status = "pending";

    @Column(name = "requested_by")
    @Setter private UUID requestedBy;

    @Column(name = "reviewed_by")
    @Setter private UUID reviewedBy;

    @Column(name = "reviewed_at")
    @Setter private Instant reviewedAt;

    @Column(name = "rejection_reason")
    @Setter @Size(max = 2000) private String rejectionReason;

    @Column(name = "push_sent_at")
    @Setter private Instant pushSentAt;

    @Column(name = "push_sent_count")
    @Setter private Integer pushSentCount;

    @Column(name = "push_error")
    @Setter @Size(max = 512) private String pushError;

    @Column(name = "deleted_at")
    @Setter private Instant deletedAt;
}
