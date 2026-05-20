package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "restaurant_restitutions")
@Getter
public class RestaurantRestitution extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id")
    @Setter private UUID tenantId;

    @Column(name = "restaurant_id", nullable = false)
    @Setter private UUID restaurantId;

    @Column(nullable = false, precision = 12, scale = 2)
    @Setter private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false)
    @Setter private Integer points = 0;

    @Column(length = 1024) @Setter private String reason;

    @Column(nullable = false, length = 64)
    @Setter private String status = "pending";
}
