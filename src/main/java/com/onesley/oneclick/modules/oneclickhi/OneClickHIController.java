package com.onesley.oneclick.modules.oneclickhi;

import com.onesley.oneclick.modules.oneclickhi.api.OneClickHIDtos.*;
import com.onesley.oneclick.modules.oneclickhi.internal.OneClickHIService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/oneclickhi")
@Tag(name = "OneClick HI", description = "Sprint H — whitelabel HR/payroll invoicing")
public class OneClickHIController {

    private final OneClickHIService service;

    public OneClickHIController(OneClickHIService service) {
        this.service = service;
    }

    // Bug 32 (Batch C RBAC v2) — double-binding sur tous les endpoints OneClickHI (RESOURCE=FINANCIAL).
    @GetMapping("/invoices")
    @Operation(summary = "Liste factures OneClickHI")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR') or hasAuthority('VIEW:FINANCIAL')")
    public List<OneClickHIInvoiceDto> findAll(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) UUID tenantId
    ) {
        return service.findAll(restaurantId, tenantId);
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("isAuthenticated() or hasAuthority('VIEW:FINANCIAL')")
    public OneClickHIInvoiceDto findById(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN') or hasAuthority('CREATE:FINANCIAL')")
    public ResponseEntity<OneClickHIInvoiceDto> create(@Valid @RequestBody OneClickHIInvoiceCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PatchMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN') or hasAuthority('UPDATE:FINANCIAL')")
    public OneClickHIInvoiceDto patch(@PathVariable UUID id, @Valid @RequestBody OneClickHIInvoicePatchDto dto) {
        return service.patch(id, dto);
    }

    @DeleteMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN') or hasAuthority('DELETE:FINANCIAL')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cockpit")
    @Operation(summary = "Cockpit OneClickHI (KPIs platform-wide)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN') or hasAuthority('VIEW:FINANCIAL')")
    public OneClickHICockpitDto cockpit() { return service.cockpit(); }

    @GetMapping("/restaurant-hi/{restaurantId}")
    @Operation(summary = "Stats HI pour un restaurant")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR') or hasAuthority('VIEW:FINANCIAL')")
    public RestaurantHIDto restaurantHI(@PathVariable UUID restaurantId) {
        return service.restaurantHI(restaurantId);
    }

    @GetMapping("/restaurant-hi/{restaurantId}/charts")
    @Operation(summary = "Charts HI mensuels pour un restaurant")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR') or hasAuthority('VIEW:FINANCIAL')")
    public List<RestaurantHIChartPointDto> restaurantHICharts(
        @PathVariable UUID restaurantId,
        @RequestParam(defaultValue = "12") int months
    ) {
        return service.restaurantHICharts(restaurantId, months);
    }
}
