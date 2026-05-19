package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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

    @Column @Setter private String reason;

    @Column(nullable = false, length = 32)
    @Setter @Size(max = 32) @NotBlank @Pattern(regexp = "^(pending|approved|rejected|paid)$") private String status = "pending";
}
