package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

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

    @Column(nullable = false, length = 255)
    @Setter @Size(max = 255) @NotBlank private String name;

    @Column @Setter private String description;

    @Column(name = "owner_id")
    @Setter private UUID ownerId;

    @Column(name = "logo_url")
    @Setter @Size(max = 1024) private String logoUrl;

    @Column(nullable = false, length = 32)
    @Setter @Size(max = 32) @NotBlank private String status = "actif";

    @Column(name = "deleted_at")
    @Setter private Instant deletedAt;
}
