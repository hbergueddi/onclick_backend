package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.core.identity.api.User;
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
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantZoneCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantZoneDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
public class RestaurantSubResourceService {

    private final RestaurantStaffRepository staffRepository;
    private final MealServiceRepository mealServiceRepository;
    private final RestaurantZoneRepository zoneRepository;
    private final RestaurantTableRepository tableRepository;
    private final RestaurantRepository restaurantRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public RestaurantSubResourceService(
        RestaurantStaffRepository staffRepository,
        MealServiceRepository mealServiceRepository,
        RestaurantZoneRepository zoneRepository,
        RestaurantTableRepository tableRepository,
        RestaurantRepository restaurantRepository
    ) {
        this.staffRepository = staffRepository;
        this.mealServiceRepository = mealServiceRepository;
        this.zoneRepository = zoneRepository;
        this.tableRepository = tableRepository;
        this.restaurantRepository = restaurantRepository;
    }

    private Restaurant requireRestaurant(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId)
            .filter(r -> r.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Restaurant", restaurantId));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Staff
    // ═══════════════════════════════════════════════════════════════════════

    public List<RestaurantStaffDto> listStaff(UUID restaurantId) {
        return staffRepository.findAllByRestaurantId(restaurantId).stream()
            .filter(s -> !s.isDeleted())
            .map(RestaurantStaff::toDto)
            .toList();
    }

    public List<RestaurantStaffDto> findStaffByUser(UUID userId) {
        return staffRepository.findAllByUserId(userId).stream()
            .filter(s -> !s.isDeleted())
            .map(RestaurantStaff::toDto)
            .toList();
    }

    @Transactional
    public RestaurantStaffDto addStaff(UUID restaurantId, RestaurantStaffCreateDto dto) {
        Restaurant restaurant = requireRestaurant(restaurantId);
        User userRef = entityManager.getReference(User.class, dto.userId());
        RestaurantStaff staff = new RestaurantStaff(UUID.randomUUID(), restaurant, userRef, dto.roleCode());
        return staffRepository.save(staff).toDto();
    }

    @Transactional
    public RestaurantStaffDto patchStaff(UUID id, RestaurantStaffPatchDto dto) {
        RestaurantStaff staff = staffRepository.findById(id)
            .filter(s -> !s.isDeleted())
            .orElseThrow(() -> new NotFoundException("RestaurantStaff", id));
        if (dto.roleCode() != null && !dto.roleCode().isBlank()) {
            staff.setRoleCode(dto.roleCode());
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
