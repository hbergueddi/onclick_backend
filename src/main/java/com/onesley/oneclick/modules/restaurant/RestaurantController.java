package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.search.SearchRequest;
import com.onesley.oneclick.search.Searchable;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.security.RestaurantAccessGuard;
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
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.modules.restaurant.api.BusinessHourDtos.BusinessHourDto;
import com.onesley.oneclick.modules.restaurant.api.BusinessHourDtos.BusinessHoursPutDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantPatchDto;
import com.onesley.oneclick.modules.restaurant.api.StaffTransferDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.MealServiceCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.MealServiceDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.MealServiceOverviewDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.MealServicePatchDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantStaffCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantStaffDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantStaffPatchDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantTableCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantTablePatchDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantZonePatchDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantTableDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantZoneCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantZoneDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantAnnouncementDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantAnnouncementCreateDto;
import com.onesley.oneclick.modules.restaurant.internal.BusinessHourService;
import com.onesley.oneclick.modules.restaurant.internal.RestaurantAnnouncementService;
import com.onesley.oneclick.modules.restaurant.internal.Restaurant;
import com.onesley.oneclick.modules.restaurant.internal.RestaurantCatalogService;
import com.onesley.oneclick.modules.restaurant.internal.RestaurantRepository;
import com.onesley.oneclick.modules.restaurant.internal.RestaurantSubResourceService;
import com.onesley.oneclick.security.TenantScope;
import org.springframework.data.jpa.domain.Specification;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/restaurants")
@Tag(name = "Restaurants", description = "Catalogue restaurants partenaires")
@RequiredArgsConstructor
public class RestaurantController {

    /** Whitelist Phase 4 §6.3 — champs filtrables/sortables. */
    private static final Set<String> SEARCHABLE_FIELDS = Set.of(
        "tenantId", "name", "city", "status",
        "latitude", "longitude", "createdAt", "updatedAt"
    );

    private final RestaurantCatalogService service;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantSubResourceService subResourceService;
    private final BusinessHourService businessHourService;
    private final UserRepository userRepository;
    private final RestaurantAccessGuard restaurantAccessGuard;
    private final RestaurantAnnouncementService announcementService;
    private final TenantScope tenantScope;

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

    // ═══════════════════════════════════════════════════════════════════════
    //  Bug 32 (RBAC v2 — Pilote) — Pattern senior VERB:RESOURCE
    // ═══════════════════════════════════════════════════════════════════════
    //
    // RBAC v2 : TOUS les endpoints en hasAuthority('VERB:RESOURCE'). Les
    // sous-ressources STAFF/SERVICES/ZONES/TABLES ont leur menu dédié depuis le
    // catalogue V32 (plus de hasAnyRole). GET / et GET /{id} restent PUBLICS
    // (catalogue). GET /staff/by-user/{id} reste isAuthenticated (self/owner).

    @PostMapping
    @Operation(summary = "Crée un restaurant")
    @PreAuthorize("hasAuthority('CREATE:RESTAURANTS')")
    public ResponseEntity<RestaurantDto> create(@Valid @RequestBody RestaurantCreateDto dto) {
        RestaurantDto r = service.create(dto);
        return ResponseEntity.created(URI.create("/api/restaurants/" + r.id())).body(r);
    }

    @PatchMapping("/{id}")
    @Operation(
        summary = "Patch partiel d'un restaurant — Sprint G.2.2",
        description = "Mise à jour partielle. Tous les champs DTO optionnels. " +
                      "Owner du restaurant (staff_role=owner) ou SUPERADMIN/GROUP_ADMIN."
    )
    @PreAuthorize("hasAuthority('UPDATE:RESTAURANTS')")
    public RestaurantDto patch(@PathVariable UUID id, @Valid @RequestBody RestaurantPatchDto dto) {
        return service.patch(id, dto);
    }

    @PostMapping("/{id}/onboarding-complete")
    @Operation(
        summary = "E1 — marque l'onboarding self-service du restaurant comme terminé",
        description = "Pose restaurants.onboarding_completed_at = now(). Appelé en fin de wizard " +
                      "1re connexion owner. Owner du restaurant (staff actif) ou SUPERADMIN/GROUP_ADMIN."
    )
    @PreAuthorize("hasAuthority('UPDATE:RESTAURANTS')")
    public RestaurantDto completeOnboarding(@PathVariable UUID id) {
        // ABAC : seul un staff actif / admin du restaurant clôture SON onboarding
        // (cohérent avec les sous-ressources staff/services/zones/tables).
        restaurantAccessGuard.requireAdminOrActiveStaffOf(id);
        return service.markOnboardingComplete(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete d'un restaurant")
    @PreAuthorize("hasAuthority('DELETE:RESTAURANTS')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.softDelete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/search")
    @Operation(summary = "Recherche dynamique (Phase 4 §6.3) — 12 opérateurs + whitelist")
    @PreAuthorize("hasAuthority('VIEW:RESTAURANTS')")
    public PageResponse<RestaurantDto> search(@RequestBody SearchRequest req) {
        // Périmètre tenant (fuite de périmètre) : base spec non contournable par les critères du client
        // → un client ne peut pas énumérer les restaurants d'un programme (PCC/HOMU) hors de son
        // périmètre via /search. SUPERADMIN (visibleTenantIdsOrNull()==null) → recherche globale.
        java.util.Set<UUID> visible = tenantScope.visibleTenantIdsOrNull();
        Specification<Restaurant> tenantSpec = (visible == null)
            ? null
            : (root, q, cb) -> root.get("tenantId").in(visible);
        return PageResponse.from(
            Searchable.execute(restaurantRepository, req, SEARCHABLE_FIELDS, Restaurant::toDto, tenantSpec)
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Horaires d'ouverture (table normalisée business_hours) — wizard E1
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/{restaurantId}/business-hours")
    @Operation(summary = "Horaires d'ouverture structurés d'un restaurant (table business_hours)")
    @PreAuthorize("hasAuthority('VIEW:RESTAURANTS')")
    public List<BusinessHourDto> listBusinessHours(@PathVariable UUID restaurantId) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        return businessHourService.getForRestaurant(restaurantId);
    }

    @PutMapping("/{restaurantId}/business-hours")
    @Operation(
        summary = "Remplace les horaires d'ouverture d'un restaurant (wizard onboarding E1)",
        description = "Remplacement complet de la semaine. Liste vide = fermé tous les jours. " +
                      "Owner du restaurant (staff actif) ou SUPERADMIN/GROUP_ADMIN."
    )
    @PreAuthorize("hasAuthority('UPDATE:RESTAURANTS')")
    public List<BusinessHourDto> putBusinessHours(
        @PathVariable UUID restaurantId,
        @Valid @RequestBody BusinessHoursPutDto dto
    ) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        return businessHourService.replaceForRestaurant(restaurantId, dto);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Annonce éphémère 24h (Gap #6 — port legacy 14/05) — staff publie, membre voit
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/{restaurantId}/announcement")
    @Operation(summary = "Annonce active 24h d'un restaurant (PUBLIC — bannière fiche spotlight). 200 avec corps, ou corps vide si aucune.")
    // PUBLIC : la bannière apparaît sur la fiche restaurant (spotlight) accessible sans auth
    // (mirroir de GET /{id}). Whitelist correspondante dans SecurityConfig.
    public ResponseEntity<RestaurantAnnouncementDto> getActiveAnnouncement(@PathVariable UUID restaurantId) {
        RestaurantAnnouncementDto active = announcementService.getActive(restaurantId);
        // 204 si aucune annonce active (le front traite l'absence comme « pas de bannière »).
        return active == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(active);
    }

    @PostMapping("/{restaurantId}/announcement")
    @Operation(summary = "Publie une annonce 24h (owner/staff actif du resto ou admin). Remplace l'annonce active existante.")
    @PreAuthorize("hasAuthority('UPDATE:RESTAURANTS')")  // gestion de contenu resto ; ABAC staff-of-resto dans le service
    public ResponseEntity<RestaurantAnnouncementDto> publishAnnouncement(
        @PathVariable UUID restaurantId,
        @Valid @RequestBody RestaurantAnnouncementCreateDto dto
    ) {
        RestaurantAnnouncementDto created = announcementService.create(restaurantId, dto.message());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/announcements/{id}")
    @Operation(summary = "Arrête (supprime) une annonce — owner/staff actif du resto ou admin.")
    @PreAuthorize("hasAuthority('UPDATE:RESTAURANTS')")  // ABAC sur le resto propriétaire dans le service
    public ResponseEntity<Void> deleteAnnouncement(@PathVariable UUID id) {
        announcementService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Staff — junction user × restaurant (role_code: owner, manager, server…)
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/{restaurantId}/staff")
    @Operation(summary = "Liste du staff d'un restaurant")
    @PreAuthorize("hasAuthority('VIEW:STAFF')")
    public List<RestaurantStaffDto> listStaff(@PathVariable UUID restaurantId) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        return subResourceService.listStaff(restaurantId);
    }

    @PostMapping("/{restaurantId}/staff")
    @Operation(summary = "Ajoute un staff au restaurant (owner/manager/server…)")
    @PreAuthorize("hasAuthority('CREATE:STAFF')")
    public ResponseEntity<RestaurantStaffDto> addStaff(
        @PathVariable UUID restaurantId,
        @Valid @RequestBody RestaurantStaffCreateDto dto
    ) {
        // P2 anti-takeover : seul un staff actif / admin du restaurant ajoute du staff
        // (sinon un RESTAURATEUR s'auto-ajoutait owner de n'importe quel resto).
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        RestaurantStaffDto created = subResourceService.addStaff(restaurantId, dto);
        return ResponseEntity.created(URI.create("/api/restaurants/staff/" + created.id())).body(created);
    }

    @PatchMapping("/staff/{id}")
    @Operation(summary = "Modifie le rôle/statut d'un staff")
    @PreAuthorize("hasAuthority('UPDATE:STAFF')")
    public RestaurantStaffDto patchStaff(
        @PathVariable UUID id,
        @Valid @RequestBody RestaurantStaffPatchDto dto
    ) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(subResourceService.getStaffRestaurantId(id));
        return subResourceService.patchStaff(id, dto);
    }

    @DeleteMapping("/staff/{id}")
    @Operation(summary = "Retire un staff (soft delete)")
    @PreAuthorize("hasAuthority('DELETE:STAFF')")
    public ResponseEntity<Void> deleteStaff(@PathVariable UUID id) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(subResourceService.getStaffRestaurantId(id));
        subResourceService.deleteStaff(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/staff/by-user/{userId}")
    @Operation(summary = "Liste les restaurants où un utilisateur est staff (self ou admin)")
    // RBAC v2 : VIEW:STAFF (révoqué au CLIENT en V36 — données opérationnelles
    // ProDesk). ABAC : requireOwnerOrAdmin restreint à soi-même (lookup de session
    // AuthContext) ou admin. Le CLIENT n'appelle jamais cet endpoint (hydrate front
    // skip pour role=client) ; un appel direct CLIENT → 403 (cohérent V36).
    @PreAuthorize("hasAuthority('VIEW:STAFF')")
    public List<RestaurantStaffDto> findStaffByUser(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return subResourceService.findStaffByUser(userId);
    }

    @GetMapping("/staffed-ids")
    @Operation(
        summary = "IDs des restaurants ayant ≥ 1 staff actif — dashboard admin (alerte « Sans équipe »).",
        description = "Agrégat cross-restaurant réservé au dashboard admin (VIEW:ANALYTICS, SUPERADMIN). "
                    + "Remplace le scan legacy supabase.from(restaurant_staff)."
    )
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public List<UUID> listStaffedRestaurantIds() {
        return subResourceService.listStaffedRestaurantIds();
    }

    @PostMapping("/staff/transfer")
    @Operation(
        summary = "Sprint G.5 — Transfert staff entre restos (port EF transfer-staff)",
        description = "Soft delete source + INSERT target atomique. RBAC : SUPERADMIN/GROUP_ADMIN."
    )
    @PreAuthorize("hasAuthority('UPDATE:STAFF')")
    public RestaurantStaffDto transferStaff(@Valid @RequestBody StaffTransferDto.TransferDto dto) {
        // Transfert = staff/admin des DEUX restos (source ET cible) — pas un restaurateur tiers.
        restaurantAccessGuard.requireAdminOrActiveStaffOf(dto.sourceRestaurantId());
        restaurantAccessGuard.requireAdminOrActiveStaffOf(dto.targetRestaurantId());
        return subResourceService.transferStaff(
            dto.staffId(), dto.sourceRestaurantId(), dto.targetRestaurantId()
        );
    }

    @PostMapping("/staff/invite")
    @Operation(
        summary = "Sprint G.5 — Invite team member par email/phone (port EF invite-team-member)",
        description = "Si user existe → ajout staff direct. Sinon V1 retourne userExists=false " +
                      "(V2 backend : envoyer email Resend + placeholder)."
    )
    @PreAuthorize("hasAuthority('CREATE:STAFF')")
    public StaffTransferDto.InviteResultDto inviteStaff(@Valid @RequestBody StaffTransferDto.InviteDto dto) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(dto.restaurantId());
        return subResourceService.inviteStaff(dto, userRepository);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Services repas (brunch / déjeuner / dîner)
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/services/overview")
    @Operation(summary = "Vue admin consolidée des quotas Click&Go (tous restaurants, créneaux actifs)")
    @PreAuthorize("hasAuthority('VIEW:ANALYTICS')")
    public List<MealServiceOverviewDto> servicesOverview() {
        return subResourceService.listAllServicesOverview();
    }

    @GetMapping("/{restaurantId}/services")
    @Operation(summary = "Liste des créneaux service d'un restaurant")
    @PreAuthorize("hasAuthority('VIEW:SERVICES')")
    public List<MealServiceDto> listServices(@PathVariable UUID restaurantId) {
        return subResourceService.listServices(restaurantId);
    }

    @PostMapping("/{restaurantId}/services")
    @Operation(summary = "Crée un créneau service (brunch, déjeuner, dîner)")
    @PreAuthorize("hasAuthority('CREATE:SERVICES')")
    public ResponseEntity<MealServiceDto> addService(
        @PathVariable UUID restaurantId,
        @Valid @RequestBody MealServiceCreateDto dto
    ) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        MealServiceDto created = subResourceService.addService(restaurantId, dto);
        return ResponseEntity.created(URI.create("/api/restaurants/services/" + created.id())).body(created);
    }

    @PatchMapping("/services/{id}")
    @Operation(summary = "Modifie un créneau service")
    @PreAuthorize("hasAuthority('UPDATE:SERVICES')")
    public MealServiceDto patchService(
        @PathVariable UUID id,
        @Valid @RequestBody MealServicePatchDto dto
    ) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(subResourceService.getServiceRestaurantId(id));
        return subResourceService.patchService(id, dto);
    }

    @DeleteMapping("/services/{id}")
    @Operation(summary = "Supprime un créneau service")
    @PreAuthorize("hasAuthority('DELETE:SERVICES')")
    public ResponseEntity<Void> deleteService(@PathVariable UUID id) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(subResourceService.getServiceRestaurantId(id));
        subResourceService.deleteService(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Zones (Terrasse, Salle, Bar) — plan de salle ProDesk
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/{restaurantId}/zones")
    @Operation(summary = "Liste des zones d'un restaurant")
    @PreAuthorize("hasAuthority('VIEW:ZONES')")
    public List<RestaurantZoneDto> listZones(@PathVariable UUID restaurantId) {
        return subResourceService.listZones(restaurantId);
    }

    @PostMapping("/{restaurantId}/zones")
    @Operation(summary = "Crée une zone (Terrasse, Salle, Bar…)")
    @PreAuthorize("hasAuthority('CREATE:ZONES')")
    public ResponseEntity<RestaurantZoneDto> addZone(
        @PathVariable UUID restaurantId,
        @Valid @RequestBody RestaurantZoneCreateDto dto
    ) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        RestaurantZoneDto created = subResourceService.addZone(restaurantId, dto);
        return ResponseEntity.created(URI.create("/api/restaurants/zones/" + created.id())).body(created);
    }

    @PatchMapping("/zones/{id}")
    @Operation(summary = "Modifie une zone (nom, type, description, capacité, statut) — PATCH partiel")
    @PreAuthorize("hasAuthority('UPDATE:ZONES')")
    public RestaurantZoneDto patchZone(@PathVariable UUID id, @Valid @RequestBody RestaurantZonePatchDto dto) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(subResourceService.getZoneRestaurantId(id));
        return subResourceService.patchZone(id, dto);
    }

    @DeleteMapping("/zones/{id}")
    @Operation(summary = "Supprime une zone (les tables liées sont supprimées en cascade DB)")
    @PreAuthorize("hasAuthority('DELETE:ZONES')")
    public ResponseEntity<Void> deleteZone(@PathVariable UUID id) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(subResourceService.getZoneRestaurantId(id));
        subResourceService.deleteZone(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Tables (rattachées à une zone)
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/{restaurantId}/tables")
    @Operation(summary = "Liste des tables d'un restaurant (toutes zones confondues)")
    @PreAuthorize("hasAuthority('VIEW:TABLES')")
    public List<RestaurantTableDto> listTables(@PathVariable UUID restaurantId) {
        return subResourceService.listTables(restaurantId);
    }

    @PostMapping("/{restaurantId}/tables")
    @Operation(summary = "Crée une table (rattachée à une zone du restaurant)")
    @PreAuthorize("hasAuthority('CREATE:TABLES')")
    public ResponseEntity<RestaurantTableDto> addTable(
        @PathVariable UUID restaurantId,
        @Valid @RequestBody RestaurantTableCreateDto dto
    ) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(restaurantId);
        RestaurantTableDto created = subResourceService.addTable(restaurantId, dto);
        return ResponseEntity.created(URI.create("/api/restaurants/tables/" + created.id())).body(created);
    }

    @PatchMapping("/tables/{id}")
    @Operation(summary = "Modifie une table (zone, numéro, places, forme, position, statut) — PATCH partiel")
    @PreAuthorize("hasAuthority('UPDATE:TABLES')")
    public RestaurantTableDto patchTable(@PathVariable UUID id, @Valid @RequestBody RestaurantTablePatchDto dto) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(subResourceService.getTableRestaurantId(id));
        return subResourceService.patchTable(id, dto);
    }

    @DeleteMapping("/tables/{id}")
    @Operation(summary = "Supprime une table")
    @PreAuthorize("hasAuthority('DELETE:TABLES')")
    public ResponseEntity<Void> deleteTable(@PathVariable UUID id) {
        restaurantAccessGuard.requireAdminOrActiveStaffOf(subResourceService.getTableRestaurantId(id));
        subResourceService.deleteTable(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
