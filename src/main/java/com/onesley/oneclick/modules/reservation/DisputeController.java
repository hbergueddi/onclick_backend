package com.onesley.oneclick.modules.reservation;

import com.onesley.oneclick.modules.reservation.api.NoShowDisputeDtos.CreateDisputeDto;
import com.onesley.oneclick.modules.reservation.api.NoShowDisputeDtos.NoShowDisputeDto;
import com.onesley.oneclick.modules.reservation.api.NoShowDisputeDtos.ResolveDisputeDto;
import com.onesley.oneclick.modules.reservation.internal.DisputeDashboardPublisher;
import com.onesley.oneclick.modules.reservation.internal.NoShowDisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller — contestation no-show (Feature #3).
 *
 * <p>RBAC v2 senior strict : <b>exclusivement</b> {@code hasAuthority('VERB:DISPUTES')}
 * (jamais {@code isAuthenticated}/{@code hasRole}/{@code hasAnyRole}). Le scoping fin
 * (ownership client, staff-of-restaurant, phase d'escalade) est délégué au
 * {@link NoShowDisputeService} — RBAC autorise grossièrement, ABAC restreint finement.
 *
 * <p>Endpoints sous {@code /api/reservations/{id}/disputes} (création + listing par
 * réservation, dans le bounded context reservation) et {@code /api/disputes}
 * (dashboard transversal + résolution).
 *
 * <p>Temps réel : après création/résolution, on déclenche un push STOMP via
 * {@link DisputeDashboardPublisher} (dashboard resto/support — pas de polling).
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Disputes", description = "Contestation no-show (Feature #3)")
@RequiredArgsConstructor
public class DisputeController {

    private final NoShowDisputeService service;
    private final DisputeDashboardPublisher dashboardPublisher;

    @PostMapping("/reservations/{id}/disputes")
    @Operation(summary = "Le client conteste un no-show (phase resto 0-1h, support 1-48h)")
    @PreAuthorize("hasAuthority('CREATE:DISPUTES')")
    public ResponseEntity<NoShowDisputeDto> create(
        @PathVariable UUID id,
        @Valid @RequestBody CreateDisputeDto dto
    ) {
        NoShowDisputeDto created = service.create(id, dto);
        dashboardPublisher.pushNow(); // temps réel dashboard resto/support
        return ResponseEntity.created(URI.create("/api/disputes/" + created.id())).body(created);
    }

    @GetMapping("/reservations/{id}/disputes")
    @Operation(summary = "Liste les contestations d'une réservation (client-owner / staff / admin)")
    @PreAuthorize("hasAuthority('VIEW:DISPUTES')")
    public List<NoShowDisputeDto> findByReservation(@PathVariable UUID id) {
        return service.findByReservation(id);
    }

    @GetMapping("/disputes")
    @Operation(
        summary = "Dashboard contestations — filtres status/phase, scoped par restaurant (ABAC)",
        description = "Admin → toutes ; restaurateur/staff → uniquement les contestations de leurs restaurants."
    )
    @PreAuthorize("hasAuthority('VIEW:DISPUTES')")
    public List<NoShowDisputeDto> findAll(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String phase
    ) {
        return service.findAll(status, phase);
    }

    @PatchMapping("/disputes/{id}")
    @Operation(
        summary = "Résout une contestation (accepted/refused)",
        description = "Phase resto → staff du restaurant ou admin ; phase support → support/admin. "
                    + "accepted → reverse la pénalité no_show (event loyalty)."
    )
    @PreAuthorize("hasAuthority('UPDATE:DISPUTES')")
    public NoShowDisputeDto resolve(
        @PathVariable UUID id,
        @Valid @RequestBody ResolveDisputeDto dto
    ) {
        NoShowDisputeDto resolved = service.resolve(id, dto);
        dashboardPublisher.pushNow(); // temps réel dashboard resto/support
        return resolved;
    }
}
