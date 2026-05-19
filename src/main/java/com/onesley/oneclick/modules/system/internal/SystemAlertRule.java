package com.onesley.oneclick.modules.system.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "system_alert_rules")
@Getter
public class SystemAlertRule extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 255)
    @Setter @Size(max = 255) @NotBlank private String name;

    @Column(name = "condition_expr", nullable = false)
    @Setter @Size(max = 512) @NotBlank private String conditionExpr;

    @Column(nullable = false, length = 32)
    @Setter @Size(max = 32) @NotBlank private String severity = "warning";

    @Column(nullable = false)
    @Setter private Boolean enabled = true;
}
