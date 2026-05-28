package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.MealServiceCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.MealServiceDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.MealServicePatchDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantStaffCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantStaffDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantStaffPatchDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantTableCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantTableDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantTablePatchDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantZoneCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantZoneDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantZonePatchDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * Service CRUD pour les sous-ressources d'un restaurant : staff, services (repas),
 * zones et tables.
 *
 * <p>Découplé de {@link RestaurantCatalogService} pour conserver ce dernier
 * focalisé sur le cycle de vie du Restaurant (cache, soft delete). Ici, on
 * n'utilise pas de cache (entités modifiées fréquemment côté ProDesk).
 *
 * <p>Pattern soft-delete uniquement sur {@link RestaurantStaff} (la seule
 * entité étendant le soft-delete). Les autres (zones, tables, mealservices)
 * sont en hard-delete (DB ON DELETE CASCADE).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RestaurantSubResourceService {

    private final RestaurantStaffRepository staffRepository;
    private final MealServiceRepository mealServiceRepository;
    private final RestaurantZoneRepository zoneRepository;
    private final RestaurantTableRepository tableRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository; // domaine identity (API publique) — enrichissement profils staff

    @PersistenceContext
    private EntityManager entityManager;

    private Restaurant requireRestaurant(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId)
            .filter(r -> r.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Restaurant", restaurantId));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Staff
    // ═══════════════════════════════════════════════════════════════════════

    public List<RestaurantStaffDto> listStaff(UUID restaurantId) {
        List<RestaurantStaffDto> base = staffRepository.findAllByRestaurantId(restaurantId).stream()
            .filter(s -> !s.isDeleted())
            .map(RestaurantStaff::toDto)
            .toList();

        // Enrichissement serveur-side : profil de chaque membre via l'API publique du
        // domaine identity (évite que le front appelle /api/users/by-ids = VIEW:USERS,
        // refusé au RESTAURATEUR/STAFF). Batch anti-N+1.
        Set<UUID> userIds = base.stream().map(RestaurantStaffDto::userId).collect(Collectors.toSet());
        Map<UUID, User> users = userIds.isEmpty() ? Map.of()
            : userRepository.findAllByIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        return base.stream().map(d -> {
            User u = users.get(d.userId());
            return new RestaurantStaffDto(d.id(), d.restaurantId(), d.userId(), d.roleCode(), d.createdAt(),
                u != null ? u.getFirstName() : null,
                u != null ? u.getLastName() : null,
                u != null ? u.getPhone() : null);
        }).toList();
    }

    public List<RestaurantStaffDto> findStaffByUser(UUID userId) {
        return staffRepository.findAllByUserId(userId).stream()
            .filter(s -> !s.isDeleted())
            .map(RestaurantStaff::toDto)
            .toList();
    }

    /** IDs des restaurants ayant ≥ 1 staff actif — dashboard admin (alerte "Sans équipe"). */
    public List<UUID> listStaffedRestaurantIds() {
        return staffRepository.findDistinctStaffedRestaurantIds();
    }

    @Transactional
    public RestaurantStaffDto addStaff(UUID restaurantId, RestaurantStaffCreateDto dto) {
        Restaurant restaurant = requireRestaurant(restaurantId);
        User userRef = entityManager.getReference(User.class, dto.userId());
        RestaurantStaff staff = new RestaurantStaff(UUID.randomUUID(), restaurant, userRef, dto.roleCode());
        return staffRepository.save(staff).toDto();
    }

    /**
     * P2 owner-check helper : restaurantId du staff (pour gate le patch/delete par
     * staff-id côté contrôleur, sans charger l'entité deux fois côté appelant).
     */
    @Transactional(readOnly = true)
    public UUID getStaffRestaurantId(UUID staffId) {
        return staffRepository.findById(staffId)
            .map(s -> s.getRestaurant().getId())
            .orElseThrow(() -> new NotFoundException("RestaurantStaff", staffId));
    }

    /** P2 owner-check helpers : restaurantId d'une sous-ressource (gate patch/delete par id). */
    @Transactional(readOnly = true)
    public UUID getServiceRestaurantId(UUID id) {
        return mealServiceRepository.findById(id)
            .map(s -> s.getRestaurant().getId())
            .orElseThrow(() -> new NotFoundException("MealService", id));
    }

    @Transactional(readOnly = true)
    public UUID getZoneRestaurantId(UUID id) {
        return zoneRepository.findById(id)
            .map(z -> z.getRestaurant().getId())
            .orElseThrow(() -> new NotFoundException("RestaurantZone", id));
    }

    @Transactional(readOnly = true)
    public UUID getTableRestaurantId(UUID id) {
        return tableRepository.findById(id)
            .map(t -> t.getZone().getRestaurant().getId())
            .orElseThrow(() -> new NotFoundException("RestaurantTable", id));
    }

    @Transactional
    public RestaurantStaffDto patchStaff(UUID id, RestaurantStaffPatchDto dto) {
        // NB : pas de filtre isDeleted() ici — la réactivation cible un staff désactivé.
        RestaurantStaff staff = staffRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("RestaurantStaff", id));
        if (dto.roleCode() != null && !dto.roleCode().isBlank()) {
            staff.setRoleCode(dto.roleCode());
        }
        if (dto.active() != null) {
            if (dto.active() && staff.isDeleted())        staff.reactivate();
            else if (!dto.active() && !staff.isDeleted()) staff.markDeleted();
        }
        // Sprint M — l'owner (UPDATE:STAFF) édite aussi le profil du membre (nom/tél)
        // du user lié. Évite PATCH /api/users/{id} (UPDATE:USERS, admin only → 403
        // RESTAURATEUR). Le module restaurant écrit déjà des users (invite/transfer).
        if (dto.firstName() != null || dto.lastName() != null || dto.phone() != null) {
            User u = staff.getUser(); // @ManyToOne(optional=false) — toujours présent
            if (dto.firstName() != null) u.setFirstName(dto.firstName());
            if (dto.lastName() != null) u.setLastName(dto.lastName());
            if (dto.phone() != null && !dto.phone().equals(u.getPhone())) {
                // Contrainte UNIQUE sur users.phone → garde explicite (409 propre).
                userRepository.findByPhone(dto.phone())
                    .filter(other -> !other.getId().equals(u.getId()))
                    .ifPresent(other -> {
                        throw new com.onesley.oneclick.exception.ConflictException("Téléphone déjà utilisé");
                    });
                u.setPhone(dto.phone());
            }
            userRepository.save(u);
        }
        return staffRepository.save(staff).toDto();
    }

    @Transactional
    public void deleteStaff(UUID id) {
        RestaurantStaff staff = staffRepository.findById(id)
            .filter(s -> !s.isDeleted())
            .orElseThrow(() -> new NotFoundException("RestaurantStaff", id));
        staff.markDeleted();
        staffRepository.save(staff);
    }

    /**
     * Sprint G.5 — Transfer staff (port EF transfer-staff Supabase).
     *
     * <p>Soft delete sur le source resto + INSERT sur le target resto, dans la
     * même transaction. Préserve le rôle. Si le target a déjà ce user en staff
     * actif, ConflictException.
     */
    @Transactional
    public RestaurantStaffDto transferStaff(
        UUID staffId, UUID sourceRestaurantId, UUID targetRestaurantId
    ) {
        if (sourceRestaurantId.equals(targetRestaurantId)) {
            throw new com.onesley.oneclick.exception.BadRequestException(
                "Restaurant source et target identiques");
        }
        // 1. Récupère le staff source
        RestaurantStaff source = staffRepository.findById(staffId)
            .filter(s -> !s.isDeleted())
            .orElseThrow(() -> new NotFoundException("RestaurantStaff", staffId));
        if (!source.getRestaurant().getId().equals(sourceRestaurantId)) {
            throw new com.onesley.oneclick.exception.BadRequestException(
                "Staff ne correspond pas au restaurant source");
        }

        // 2. Vérif anti-doublon sur target
        Restaurant target = requireRestaurant(targetRestaurantId);
        boolean alreadyStaff = staffRepository.findAllByRestaurantId(targetRestaurantId).stream()
            .anyMatch(s -> !s.isDeleted() && s.getUser().getId().equals(source.getUser().getId()));
        if (alreadyStaff) {
            throw new com.onesley.oneclick.exception.ConflictException(
                "User déjà staff actif sur le restaurant target");
        }

        // 3. Soft delete source + insert target (atomique)
        String roleCode = source.getRoleCode();
        User user = source.getUser();
        source.markDeleted();
        staffRepository.save(source);

        RestaurantStaff newStaff = new RestaurantStaff(UUID.randomUUID(), target, user, roleCode);
        return staffRepository.save(newStaff).toDto();
    }

    /**
     * Sprint G.5 — Invite team member (port EF invite-team-member Supabase).
     *
     * <p>Workflow :
     * <ol>
     *   <li>Lookup user par email OU phone via UserRepository</li>
     *   <li>Si user existe → ajout staff direct (cf addStaff)</li>
     *   <li>Si user n'existe pas → marqué pour invitation (V2 : envoyer email
     *     via Resend, créer un compte placeholder). Pour V1 retourne userExists=false.</li>
     * </ol>
     */
    @Transactional
    public com.onesley.oneclick.modules.restaurant.api.StaffTransferDto.InviteResultDto inviteStaff(
        com.onesley.oneclick.modules.restaurant.api.StaffTransferDto.InviteDto dto,
        com.onesley.oneclick.core.identity.api.UserRepository userRepo
    ) {
        if (dto.email() == null && dto.phone() == null) {
            throw new com.onesley.oneclick.exception.BadRequestException(
                "Email ou phone requis pour inviter");
        }

        // 1. Lookup user existant
        User candidate = null;
        if (dto.email() != null) {
            candidate = userRepo.findByEmailIgnoreCase(dto.email())
                .filter(u -> u.getDeletedAt() == null).orElse(null);
        }
        if (candidate == null && dto.phone() != null) {
            candidate = userRepo.findByPhone(dto.phone())
                .filter(u -> u.getDeletedAt() == null).orElse(null);
        }

        if (candidate == null) {
            // V2 backend : créer placeholder + envoyer email Resend
            return new com.onesley.oneclick.modules.restaurant.api.StaffTransferDto.InviteResultDto(
                false, null, "User non trouvé — invitation par email (V2 backend)"
            );
        }

        // 2. User trouvé → addStaff direct (avec vérif anti-doublon)
        final User found = candidate;
        boolean alreadyStaff = staffRepository.findAllByRestaurantId(dto.restaurantId()).stream()
            .anyMatch(s -> !s.isDeleted() && s.getUser().getId().equals(found.getId()));
        if (alreadyStaff) {
            throw new com.onesley.oneclick.exception.ConflictException(
                "User déjà staff actif sur ce restaurant");
        }

        Restaurant restaurant = requireRestaurant(dto.restaurantId());
        RestaurantStaff staff = new RestaurantStaff(UUID.randomUUID(), restaurant, found, dto.roleCode());
        RestaurantStaff saved = staffRepository.save(staff);
        return new com.onesley.oneclick.modules.restaurant.api.StaffTransferDto.InviteResultDto(
            true, saved.getId(), "User ajouté comme staff"
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  MealServices (créneaux brunch/déjeuner/dîner)
    // ═══════════════════════════════════════════════════════════════════════

    public List<MealServiceDto> listServices(UUID restaurantId) {
        return mealServiceRepository.findAllByRestaurantId(restaurantId).stream()
            .map(MealService::toDto)
            .toList();
    }

    @Transactional
    public MealServiceDto addService(UUID restaurantId, MealServiceCreateDto dto) {
        Restaurant restaurant = requireRestaurant(restaurantId);
        if (!dto.endTime().isAfter(dto.startTime())) {
            throw new BadRequestException("endTime doit être strictement supérieur à startTime");
        }
        MealService svc = new MealService(UUID.randomUUID(), restaurant, dto.name(), dto.startTime(), dto.endTime());
        return mealServiceRepository.save(svc).toDto();
    }

    @Transactional
    public MealServiceDto patchService(UUID id, MealServicePatchDto dto) {
        MealService svc = mealServiceRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("MealService", id));
        if (dto.name() != null && !dto.name().isBlank()) {
            svc.setName(dto.name());
        }
        if (dto.startTime() != null) {
            svc.setStartTime(dto.startTime());
        }
        if (dto.endTime() != null) {
            svc.setEndTime(dto.endTime());
        }
        if (!svc.getEndTime().isAfter(svc.getStartTime())) {
            throw new BadRequestException("endTime doit être strictement supérieur à startTime");
        }
        return mealServiceRepository.save(svc).toDto();
    }

    @Transactional
    public void deleteService(UUID id) {
        if (!mealServiceRepository.existsById(id)) {
            throw new NotFoundException("MealService", id);
        }
        mealServiceRepository.deleteById(id);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Zones (Terrasse, Salle, Bar)
    // ═══════════════════════════════════════════════════════════════════════

    public List<RestaurantZoneDto> listZones(UUID restaurantId) {
        return zoneRepository.findAllByRestaurantId(restaurantId).stream()
            .map(RestaurantZone::toDto)
            .toList();
    }

    @Transactional
    public RestaurantZoneDto addZone(UUID restaurantId, RestaurantZoneCreateDto dto) {
        Restaurant restaurant = requireRestaurant(restaurantId);
        RestaurantZone zone = new RestaurantZone(UUID.randomUUID(), restaurant, dto.name());
        if (dto.type() != null && !dto.type().isBlank())     zone.setType(dto.type());
        if (dto.description() != null)                        zone.setDescription(dto.description());
        if (dto.capacity() != null)                           zone.setCapacity(dto.capacity());
        if (dto.status() != null && !dto.status().isBlank())  zone.setStatus(dto.status());
        return zoneRepository.save(zone).toDto();
    }

    /** Patch partiel d'une zone (V50) — seuls les champs non-null sont appliqués. */
    @Transactional
    public RestaurantZoneDto patchZone(UUID id, RestaurantZonePatchDto dto) {
        RestaurantZone zone = zoneRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("RestaurantZone", id));
        if (dto.name() != null && !dto.name().isBlank())      zone.setName(dto.name());
        if (dto.type() != null && !dto.type().isBlank())      zone.setType(dto.type());
        if (dto.description() != null)                        zone.setDescription(dto.description());
        if (dto.capacity() != null)                           zone.setCapacity(dto.capacity());
        if (dto.status() != null && !dto.status().isBlank())  zone.setStatus(dto.status());
        return zoneRepository.save(zone).toDto();
    }

    @Transactional
    public void deleteZone(UUID id) {
        if (!zoneRepository.existsById(id)) {
            throw new NotFoundException("RestaurantZone", id);
        }
        // ON DELETE CASCADE en DB sur restaurant_tables.zone_id : les tables liées
        // sont supprimées atomiquement par Postgres.
        zoneRepository.deleteById(id);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Tables (rattachées à une zone)
    // ═══════════════════════════════════════════════════════════════════════

    public List<RestaurantTableDto> listTables(UUID restaurantId) {
        return tableRepository.findAllByRestaurantId(restaurantId).stream()
            .map(RestaurantTable::toDto)
            .toList();
    }

    @Transactional
    public RestaurantTableDto addTable(UUID restaurantId, RestaurantTableCreateDto dto) {
        // Garde-fou : la zone passée doit appartenir au restaurant fourni
        RestaurantZone zone = zoneRepository.findById(dto.zoneId())
            .orElseThrow(() -> new NotFoundException("RestaurantZone", dto.zoneId()));
        if (!zone.getRestaurantId().equals(restaurantId)) {
            throw new BadRequestException("La zone " + dto.zoneId() + " n'appartient pas au restaurant " + restaurantId);
        }
        RestaurantTable table = new RestaurantTable(UUID.randomUUID(), zone, dto.tableNumber(), dto.seats());
        if (dto.shape() != null && !dto.shape().isBlank())   table.setShape(dto.shape());
        if (dto.position() != null)                          table.setPosition(dto.position());
        if (dto.status() != null && !dto.status().isBlank()) table.setStatus(dto.status());
        return tableRepository.save(table).toDto();
    }

    /** Patch partiel d'une table (V50). {@code zoneId} déplace la table vers une autre
     * zone du MÊME restaurant (garde-fou anti cross-restaurant). */
    @Transactional
    public RestaurantTableDto patchTable(UUID id, RestaurantTablePatchDto dto) {
        RestaurantTable table = tableRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("RestaurantTable", id));
        if (dto.zoneId() != null && !dto.zoneId().equals(table.getZoneId())) {
            RestaurantZone newZone = zoneRepository.findById(dto.zoneId())
                .orElseThrow(() -> new NotFoundException("RestaurantZone", dto.zoneId()));
            if (!newZone.getRestaurantId().equals(table.getZone().getRestaurantId())) {
                throw new BadRequestException("La zone cible n'appartient pas au même restaurant que la table");
            }
            table.setZone(newZone);
        }
        if (dto.tableNumber() != null && !dto.tableNumber().isBlank()) table.setTableNumber(dto.tableNumber());
        if (dto.seats() != null)                             table.setSeats(dto.seats());
        if (dto.shape() != null && !dto.shape().isBlank())   table.setShape(dto.shape());
        if (dto.position() != null)                          table.setPosition(dto.position());
        if (dto.status() != null && !dto.status().isBlank()) table.setStatus(dto.status());
        return tableRepository.save(table).toDto();
    }

    @Transactional
    public void deleteTable(UUID id) {
        if (!tableRepository.existsById(id)) {
            throw new NotFoundException("RestaurantTable", id);
        }
        tableRepository.deleteById(id);
    }
}
