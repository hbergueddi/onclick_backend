package com.onesley.oneclick.modules.oneclickhi;

import com.onesley.oneclick.modules.oneclickhi.api.OneClickHIDtos.*;
import com.onesley.oneclick.modules.oneclickhi.internal.OneClickHIService;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.security.RestaurantAccessGuard;
import com.onesley.oneclick.exception.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/oneclickhi")
@Tag(name = "OneClick HI", description = "Sprint H — whitelabel HR/payroll invoicing")
@RequiredArgsConstructor
public class OneClickHIController {

    private final OneClickHIService service;
    private final RestaurantAccessGuard restaurantAccessGuard;

    // Bug 32 (Batch C RBAC v2) — RBAC v2 senior strict sur tous les endpoints OneClickHI (RESOURCE=FINANCIAL).
    @GetMapping("/invoices")
    @Operation(summary = "Liste factures OneClickHI")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public List<OneClickHIInvoiceDto> findAll(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(required = false) UUID tenantId
    ) {
        // Non-admin : doit cibler un restaurant dont il est staff actif (anti cross-restaurant).
        if (!SecurityHelper.isAdmin()) {
            restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        }
        return service.findAll(restaurantId, tenantId);
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public OneClickHIInvoiceDto findById(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping("/invoices")
    @PreAuthorize("hasAuthority('CREATE:FINANCIAL')")
    public ResponseEntity<OneClickHIInvoiceDto> create(@Valid @RequestBody OneClickHIInvoiceCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PatchMapping("/invoices/{id}")
    @PreAuthorize("hasAuthority('UPDATE:FINANCIAL')")
    public OneClickHIInvoiceDto patch(@PathVariable UUID id, @Valid @RequestBody OneClickHIInvoicePatchDto dto) {
        return service.patch(id, dto);
    }

    @DeleteMapping("/invoices/{id}")
    @PreAuthorize("hasAuthority('DELETE:FINANCIAL')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cockpit")
    @Operation(summary = "Cockpit OneClickHI (KPIs platform-wide)")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public OneClickHICockpitDto cockpit() {
        // Agrégat plateforme (tous restos) → admin uniquement (un restaurateur a
        // VIEW:FINANCIAL mais ne doit pas voir les KPIs cross-tenant).
        if (!SecurityHelper.isAdmin()) {
            throw new ForbiddenException("Cockpit OneClickHI réservé à l'administration");
        }
        return service.cockpit();
    }

    @GetMapping("/restaurant-hi/{restaurantId}")
    @Operation(summary = "Stats HI pour un restaurant")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public RestaurantHIDto restaurantHI(@PathVariable UUID restaurantId) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        return service.restaurantHI(restaurantId);
    }

    @GetMapping("/restaurant-hi/{restaurantId}/charts")
    @Operation(summary = "Charts HI mensuels pour un restaurant")
    @PreAuthorize("hasAuthority('VIEW:FINANCIAL')")
    public List<RestaurantHIChartPointDto> restaurantHICharts(
        @PathVariable UUID restaurantId,
        @RequestParam(defaultValue = "12") int months
    ) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        return service.restaurantHICharts(restaurantId, months);
    }
}
