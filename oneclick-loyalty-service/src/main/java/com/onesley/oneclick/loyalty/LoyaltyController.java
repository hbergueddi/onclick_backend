package com.onesley.oneclick.loyalty;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
    public LoyaltyAccountDto findAccount(@PathVariable UUID id) {
        return service.findAccount(id);
    }

    @GetMapping("/accounts")
    @Operation(summary = "Comptes fidélité d'un client (lookup ou création auto si besoin)")
    public LoyaltyAccountDto findOrCreate(
        @RequestParam UUID clientId,
        @RequestParam UUID restaurantId
    ) {
        return service.findOrCreate(clientId, restaurantId);
    }

    @GetMapping("/accounts/by-client/{clientId}")
    @Operation(summary = "Tous les comptes fidélité d'un client (1 par restaurant)")
    public List<LoyaltyAccountDto> findByClient(@PathVariable UUID clientId) {
        return service.findByClient(clientId);
    }

    @GetMapping("/accounts/{accountId}/transactions")
    @Operation(summary = "Historique des mouvements d'un compte")
    public List<LoyaltyTransactionDto> findTransactionsByAccount(@PathVariable UUID accountId) {
        return service.findTransactionsByAccount(accountId);
    }

    @PostMapping("/earn")
    @Operation(summary = "Crédite des points (Snap2Earn). INSERT transaction + UPDATE balance dans la même tx.")
    public LoyaltyTransactionDto earn(@Valid @RequestBody LoyaltyEarnDto dto) {
        return service.earnPoints(dto);
    }

    @PostMapping("/spend")
    @Operation(summary = "Débite des points (Redemption). Refuse si solde insuffisant.")
    public LoyaltyTransactionDto spend(@Valid @RequestBody SpendDto dto) {
        return service.spendPoints(dto.clientId(), dto.restaurantId(), dto.points(), dto.reason());
    }
}
