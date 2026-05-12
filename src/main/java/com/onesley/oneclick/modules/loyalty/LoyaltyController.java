package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.modules.loyalty.api.LoyaltyAccountDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyEarnDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTransactionDto;
import com.onesley.oneclick.modules.loyalty.internal.LoyaltyService;
import com.onesley.oneclick.security.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/loyalty")
@Tag(name = "Loyalty", description = "Comptes + transactions fidélité (§6)")
public class LoyaltyController {

    private final LoyaltyService service;

    public LoyaltyController(LoyaltyService service) {
        this.service = service;
    }

    public record SpendDto(
        @NotNull UUID clientId,
        @NotNull UUID restaurantId,
        @NotNull @Min(1) Integer points,
        String reason
    ) {}

    @GetMapping("/accounts/{id}")
    @Operation(summary = "Détail d'un compte fidélité")
    @PreAuthorize("isAuthenticated()")
    public LoyaltyAccountDto findAccount(@PathVariable UUID id) {
        return service.findAccount(id);
    }

    @GetMapping("/accounts")
    @Operation(summary = "Comptes fidélité d'un client (lookup ou création auto si besoin)")
    @PreAuthorize("isAuthenticated()")
    public LoyaltyAccountDto findOrCreate(
        @RequestParam UUID clientId,
        @RequestParam UUID restaurantId
    ) {
        return service.findOrCreate(clientId, restaurantId);
    }

    @GetMapping("/accounts/by-client/{clientId}")
    @Operation(summary = "Tous les comptes fidélité d'un client (1 par restaurant)")
    @PreAuthorize("isAuthenticated()")
    public List<LoyaltyAccountDto> findByClient(@PathVariable UUID clientId) {
        SecurityHelper.requireOwnerOrAdmin(clientId);
        return service.findByClient(clientId);
    }

    @GetMapping("/accounts/{accountId}/transactions")
    @Operation(summary = "Historique des mouvements d'un compte")
    @PreAuthorize("isAuthenticated()")
    public List<LoyaltyTransactionDto> findTransactionsByAccount(@PathVariable UUID accountId) {
        return service.findTransactionsByAccount(accountId);
    }

    @PostMapping("/earn")
    @Operation(summary = "Crédite des points (Snap2Earn). INSERT transaction + UPDATE balance dans la même tx.")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'GROUP_ADMIN', 'RESTAURATEUR')")
    public LoyaltyTransactionDto earn(@Valid @RequestBody LoyaltyEarnDto dto) {
        return service.earnPoints(dto);
    }

    @PostMapping("/spend")
    @Operation(summary = "Débite des points (Redemption). Refuse si solde insuffisant.")
    @PreAuthorize("isAuthenticated()")
    public LoyaltyTransactionDto spend(@Valid @RequestBody SpendDto dto) {
        SecurityHelper.requireOwnerOrAdmin(dto.clientId());
        return service.spendPoints(dto.clientId(), dto.restaurantId(), dto.points(), dto.reason());
    }
}
