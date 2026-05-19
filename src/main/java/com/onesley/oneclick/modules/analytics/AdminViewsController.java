package com.onesley.oneclick.modules.analytics;

import com.onesley.oneclick.modules.analytics.api.AdminStatsFullDto;
import com.onesley.oneclick.modules.analytics.api.AdminViewsDtos.*;
import com.onesley.oneclick.modules.analytics.internal.AdminStatsFullService;
import com.onesley.oneclick.modules.analytics.internal.AdminViewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Admin views", description = "Sprint H — admin users + wallet + recycling-pool + HI cockpit")
public class AdminViewsController {

    private final AdminViewsService service;
    private final AdminStatsFullService statsFullService;

    public AdminViewsController(AdminViewsService service, AdminStatsFullService statsFullService) {
        this.service = service;
        this.statsFullService = statsFullService;
    }

    @GetMapping("/admin-stats-full")
    @Operation(summary = "Admin stats enrichi (compatible legacy useAdminStats — KPIs + deltas + trends + charts)")
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public AdminStatsFullDto adminStatsFull(@RequestParam(required = false) String period) {
        return statsFullService.compute(period);
    }

    @GetMapping("/admin-users")
    @Operation(summary = "Liste enrichie des users (KPIs loyalty/résas/tickets)")
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public List<AdminUserDto> findAdminUsers(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String roleCode,
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(defaultValue = "200") int limit
    ) {
        return service.findAdminUsers(search, roleCode, tenantId, limit);
    }

    @GetMapping("/admin-wallet/summary")
    @Operation(summary = "Résumé global wallet admin")
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public AdminWalletSummaryDto walletSummary() {
        return service.walletSummary();
    }

    @GetMapping("/admin-wallet/transactions")
    @Operation(
        summary = "Transactions wallet (filtres user/restaurant)",
        description = "RBAC : SUPERADMIN/GROUP_ADMIN voient tout. RESTAURATEUR/STAFF "
                    + "peuvent voir UNIQUEMENT leur propre restaurant (restaurantId "
                    + "obligatoire + check staff actif dans le service)."
    )
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public List<AdminWalletTransactionDto> walletTransactions(
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return service.walletTransactions(userId, restaurantId, limit);
    }

    @GetMapping("/recycling-pool")
    @Operation(summary = "État du pool de recyclage (KPIs wallet aggregés)")
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public RecyclingPoolDto recyclingPool() {
        return service.recyclingPool();
    }

    @GetMapping("/admin-hi-cockpit")
    @Operation(summary = "Cockpit admin OneClickHI (restos + contrats + revenue + tickets)")
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public AdminHICockpitDto adminHICockpit() {
        return service.adminHICockpit();
    }
}
