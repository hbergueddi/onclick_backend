package com.onesley.oneclick.modules.system.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "system_alert_rules")
public class SystemAlertRule extends TimestampedEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "condition_expr", nullable = false)
    private String conditionExpr;

    @Column(nullable = false, length = 32)
    private String severity = "warning";

    @Column(nullable = false)
    private Boolean enabled = true;

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getConditionExpr() { return conditionExpr; }
    public void setConditionExpr(String v) { this.conditionExpr = v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity = v; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean v) { this.enabled = v; }
}
