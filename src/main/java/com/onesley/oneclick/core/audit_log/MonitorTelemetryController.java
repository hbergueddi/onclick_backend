package com.onesley.oneclick.core.audit_log;

import com.onesley.oneclick.core.audit_log.internal.MonitorTelemetry;
import com.onesley.oneclick.core.audit_log.internal.MonitorTelemetryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Endpoint Sprint I.3 — Ingestion télémétrie app mobile (port EF monitor-telemetry).
 *
 * <p>L'app mobile envoie des batches d'events (push_register, push_receive,
 * token_register, app_startup, etc.) qui sont stockés dans {@code monitor_logs}
 * pour analyse offline via le dashboard {@code monitor.app-oneclick.net}.
 */
@RestController
@RequestMapping("/api/audit/telemetry")
@Tag(name = "Monitor telemetry", description = "Sprint I.3 — ingestion télémétrie batch app mobile")
@RequiredArgsConstructor
public class MonitorTelemetryController {

    private final MonitorTelemetryRepository repo;

    public record TelemetryEventDto(
        UUID userId,
        UUID tenantId,
        String appId,
        @NotBlank String eventType,
        Map<String, Object> eventData,
        String platform,
        String appVersion
    ) {}

    public record TelemetryBatchDto(List<TelemetryEventDto> events) {}

    @PostMapping
    @Operation(summary = "Ingère un batch d'events télémétrie (max 50/batch)")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody TelemetryBatchDto batch) {
        if (batch == null || batch.events() == null || batch.events().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "events vide"));
        }
        if (batch.events().size() > 50) {
            return ResponseEntity.badRequest().body(Map.of("error", "max 50 events par batch"));
        }
        List<MonitorTelemetry> entities = batch.events().stream().map(e -> {
            MonitorTelemetry m = new MonitorTelemetry();
            m.setUserId(e.userId());
            m.setTenantId(e.tenantId());
            m.setAppId(e.appId());
            m.setEventType(e.eventType());
            m.setEventData(e.eventData());
            m.setPlatform(e.platform());
            m.setAppVersion(e.appVersion());
            return m;
        }).toList();
        repo.saveAll(entities);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "ingested", entities.size()
        ));
    }

    @GetMapping
    @Operation(summary = "Liste recente events télémétrie (admin)")
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
    public List<MonitorTelemetry> findRecent(
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) UUID userId,
        @RequestParam(defaultValue = "200") int limit
    ) {
        var pageable = PageRequest.of(0, limit);
        if (eventType != null) return repo.findByEventType(eventType, pageable);
        if (userId != null) return repo.findByUserId(userId, pageable);
        return repo.findRecent(pageable);
    }
}
