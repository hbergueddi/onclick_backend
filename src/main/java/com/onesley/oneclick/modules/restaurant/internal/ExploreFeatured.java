package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "explore_featured")
@Getter
public class ExploreFeatured extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id")
    @Setter private UUID tenantId;

    @Column(name = "restaurant_id", nullable = false, unique = true)
    @Setter private UUID restaurantId;

    @Column(nullable = false)
    @Setter private Integer rank = 0;

    @Column(nullable = false)
    @Setter private Boolean enabled = true;

    @Column(name = "starts_at")
    @Setter private Instant startsAt;

    @Column(name = "ends_at")
    @Setter private Instant endsAt;
}
