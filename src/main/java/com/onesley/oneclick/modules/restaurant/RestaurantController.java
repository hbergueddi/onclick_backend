package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.search.SearchRequest;
import com.onesley.oneclick.search.Searchable;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.onesley.oneclick.modules.restaurant.api.RestaurantCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.MealServiceCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.MealServiceDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.MealServicePatchDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantStaffCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantStaffDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantStaffPatchDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantTableCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantTableDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantZoneCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantZoneDto;
import com.onesley.oneclick.modules.restaurant.internal.Restaurant;
import com.onesley.oneclick.modules.restaurant.internal.RestaurantCatalogService;
import com.onesley.oneclick.modules.restaurant.internal.RestaurantRepository;
import com.onesley.oneclick.modules.restaurant.internal.RestaurantSubResourceService;

@RestController
@RequestMapping("/api/restaurants")
@Tag(name = "Restaurants", description = "Catalogue restaurants partenaires")
public class RestaurantController {

    /** Whitelist Phase 4 §6.3 — champs filtrables/sortables. */
    private static final Set<String> SEARCHABLE_FIELDS = Set.of(
        "tenantId", "name", "city", "status",
        "latitude", "longitude", "createdAt", "updatedAt"
    );

    private final RestaurantCatalogService service;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantSubResourceService subResourceService;

    public RestaurantController(
        RestaurantCatalogService service,
        RestaurantRepository restaurantRepository,
        RestaurantSubResourceService subResourceService
    ) {
        this.service = service;
        this.restaurantRepository = restaurantRepository;
        this.subResourceService = subResourceService;
    }

    @GetMapping
    @Operation(summary = "Liste paginée des restaurants — filtres city + tenantId optionnels (PUBLIC catalogue)")
    // PUBLIC : catalogue accessible avant authentification (Login.tsx picker resto whitelabel).
    // Whitelist correspondante dans SecurityConfig (GET /api/restaurants).
    public PageResponse<RestaurantDto> findAll(
        @RequestParam(required = false) String city,
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAll(city, tenantId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail restaurant par UUID (PUBLIC catalogue)")
    // PUBLIC : détail accessible sans auth (utilisé en Spotlight pré-login + SEO).
    public RestaurantDto findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @Operation(summary = "Crée un restaurant")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<RestaurantDto> create(@Valid @RequestBody RestaurantCreateDto dto) {
        RestaurantDto r = service.create(dto);
        return ResponseEntity.created(URI.create("/api/restaurants/" + r.id())).body(r);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete d'un restaurant")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.softDelete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/search")
    @Operation(summary = "Recherche dynamique (Phase 4 §6.3) — 12 opérateurs + whitelist")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<RestaurantDto> search(@RequestBody SearchRequest req) {
        return PageResponse.from(
            Searchable.execute(restaurantRepository, req, SEARCHABLE_FIELDS, Restaurant::toDto)
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Staff — junction user × restaurant (role_code: owner, manager, server…)
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/{restaurantId}/staff")
    @Operation(summary = "Liste du staff d'un restaurant")
    @PreAuthorize("isAuthenticated()")
    public List<RestaurantStaffDto> listStaff(@PathVariable UUID restaurantId) {
        return subResourceService.listStaff(restaurantId);
    }

    @PostMapping("/{restaurantId}/staff")
    @Operation(summary = "Ajoute un staff au restaurant (owner/manager/server…)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public ResponseEntity<RestaurantStaffDto> addStaff(
        @PathVariable UUID restaurantId,
        @Valid @RequestBody RestaurantStaffCreateDto dto
    ) {
        RestaurantStaffDto created = subResourceService.addStaff(restaurantId, dto);
        return ResponseEntity.created(URI.create("/api/restaurants/staff/" + created.id())).body(created);
    }

    @PatchMapping("/staff/{id}")
    @Operation(summary = "Modifie le rôle/statut d'un staff")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public RestaurantStaffDto patchStaff(
        @PathVariable UUID id,
        @Valid @RequestBody RestaurantStaffPatchDto dto
    ) {
        return subResourceService.patchStaff(id, dto);
    }

    @DeleteMapping("/staff/{id}")
    @Operation(summary = "Retire un staff (soft delete)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public ResponseEntity<Void> deleteStaff(@PathVariable UUID id) {
        subResourceService.deleteStaff(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/staff/by-user/{userId}")
    @Operation(summary = "Liste les restaurants où je suis staff (owner check)")
    @PreAuthorize("isAuthenticated()")
    public List<RestaurantStaffDto> findStaffByUser(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return subResourceService.findStaffByUser(userId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Services repas (brunch / déjeuner / dîner)
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/{restaurantId}/services")
    @Operation(summary = "Liste des créneaux service d'un restaurant")
    @PreAuthorize("isAuthenticated()")
    public List<MealServiceDto> listServices(@PathVariable UUID restaurantId) {
        return subResourceService.listServices(restaurantId);
    }

    @PostMapping("/{restaurantId}/services")
    @Operation(summary = "Crée un créneau service (brunch, déjeuner, dîner)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public ResponseEntity<MealServiceDto> addService(
        @PathVariable UUID restaurantId,
        @Valid @RequestBody MealServiceCreateDto dto
    ) {
        MealServiceDto created = subResourceService.addService(restaurantId, dto);
        return ResponseEntity.created(URI.create("/api/restaurants/services/" + created.id())).body(created);
    }

    @PatchMapping("/services/{id}")
    @Operation(summary = "Modifie un créneau service")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public MealServiceDto patchService(
        @PathVariable UUID id,
        @Valid @RequestBody MealServicePatchDto dto
    ) {
        return subResourceService.patchService(id, dto);
    }

    @DeleteMapping("/services/{id}")
    @Operation(summary = "Supprime un créneau service")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public ResponseEntity<Void> deleteService(@PathVariable UUID id) {
        subResourceService.deleteService(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Zones (Terrasse, Salle, Bar) — plan de salle ProDesk
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/{restaurantId}/zones")
    @Operation(summary = "Liste des zones d'un restaurant")
    @PreAuthorize("isAuthenticated()")
    public List<RestaurantZoneDto> listZones(@PathVariable UUID restaurantId) {
        return subResourceService.listZones(restaurantId);
    }

    @PostMapping("/{restaurantId}/zones")
    @Operation(summary = "Crée une zone (Terrasse, Salle, Bar…)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public ResponseEntity<RestaurantZoneDto> addZone(
        @PathVariable UUID restaurantId,
        @Valid @RequestBody RestaurantZoneCreateDto dto
    ) {
        RestaurantZoneDto created = subResourceService.addZone(restaurantId, dto);
        return ResponseEntity.created(URI.create("/api/restaurants/zones/" + created.id())).body(created);
    }

    @DeleteMapping("/zones/{id}")
    @Operation(summary = "Supprime une zone (les tables liées sont supprimées en cascade DB)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public ResponseEntity<Void> deleteZone(@PathVariable UUID id) {
        subResourceService.deleteZone(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Tables (rattachées à une zone)
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/{restaurantId}/tables")
    @Operation(summary = "Liste des tables d'un restaurant (toutes zones confondues)")
    @PreAuthorize("isAuthenticated()")
    public List<RestaurantTableDto> listTables(@PathVariable UUID restaurantId) {
        return subResourceService.listTables(restaurantId);
    }

    @PostMapping("/{restaurantId}/tables")
    @Operation(summary = "Crée une table (rattachée à une zone du restaurant)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public ResponseEntity<RestaurantTableDto> addTable(
        @PathVariable UUID restaurantId,
        @Valid @RequestBody RestaurantTableCreateDto dto
    ) {
        RestaurantTableDto created = subResourceService.addTable(restaurantId, dto);
        return ResponseEntity.created(URI.create("/api/restaurants/tables/" + created.id())).body(created);
    }

    @DeleteMapping("/tables/{id}")
    @Operation(summary = "Supprime une table")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public ResponseEntity<Void> deleteTable(@PathVariable UUID id) {
        subResourceService.deleteTable(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
