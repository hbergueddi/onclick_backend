package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "restaurant_groups")
@Getter
public class RestaurantGroup extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id")
    @Setter private UUID tenantId;

    @Column(nullable = false)
    @Setter private String name;

    @Column @Setter private String description;

    @Column(name = "owner_id")
    @Setter private UUID ownerId;

    @Column(name = "logo_url")
    @Setter private String logoUrl;

    @Column(nullable = false, length = 32)
    @Setter private String status = "actif";

    @Column(name = "deleted_at")
    @Setter private Instant deletedAt;
}
