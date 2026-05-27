package com.onesley.oneclick.modules.restaurant.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.MealServiceCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.MealServicePatchDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantStaffCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantStaffPatchDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantTableCreateDto;
import com.onesley.oneclick.modules.restaurant.api.RestaurantSubResourceDtos.RestaurantZoneCreateDto;
import com.onesley.oneclick.modules.restaurant.api.StaffTransferDto.InviteDto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link RestaurantSubResourceService} (L3 — modules.restaurant).
 * Staff (CRUD + transfer + invite), MealServices, Zones, Tables — soft-delete + garde-fous.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class RestaurantSubResourceServiceTest {

    @Mock RestaurantStaffRepository staffRepository;
    @Mock MealServiceRepository mealServiceRepository;
    @Mock RestaurantZoneRepository zoneRepository;
    @Mock RestaurantTableRepository tableRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock EntityManager em;
    @Mock UserRepository userRepo;
    @InjectMocks RestaurantSubResourceService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "entityManager", em);
        lenient().when(em.getReference(eq(User.class), any())).thenReturn(user(UUID.randomUUID()));
        lenient().when(staffRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(mealServiceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(zoneRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(tableRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Restaurant restaurant(UUID id) {
        return new Restaurant(id, new Tenant(UUID.randomUUID(), "T", "t"), "Resto", "Casa");
    }
    private User user(UUID id) {
        return new User(id, null, "u@x.ma", "h", "U", "U");
    }
    private RestaurantStaff staff(UUID restaurantId, UUID userId, String role) {
        return new RestaurantStaff(UUID.randomUUID(), restaurant(restaurantId), user(userId), role);
    }
    private MealService meal() {
        return new MealService(UUID.randomUUID(), restaurant(UUID.randomUUID()), "Déjeuner",
            LocalTime.of(12, 0), LocalTime.of(15, 0));
    }
    private RestaurantZone zone(UUID restaurantId) {
        RestaurantZone z = new RestaurantZone(UUID.randomUUID(), restaurant(restaurantId), "Terrasse");
        ReflectionTestUtils.setField(z, "restaurantId", restaurantId);
        return z;
    }

    // ─── Staff ───────────────────────────────────────────────────────────────

    @Test
    void listStaff_filtersDeleted_andFindByUser() {
        RestaurantStaff deleted = staff(UUID.randomUUID(), UUID.randomUUID(), "owner");
        deleted.markDeleted();
        when(staffRepository.findAllByRestaurantId(any())).thenReturn(List.of(staff(UUID.randomUUID(), UUID.randomUUID(), "owner"), deleted));
        when(staffRepository.findAllByUserId(any())).thenReturn(List.of(staff(UUID.randomUUID(), UUID.randomUUID(), "manager")));
        assertThat(service.listStaff(UUID.randomUUID())).hasSize(1);
        assertThat(service.findStaffByUser(UUID.randomUUID())).hasSize(1);
    }

    @Test
    void addStaff_restaurantNotFound_andSuccess() {
        UUID rid = UUID.randomUUID();
        when(restaurantRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addStaff(rid, new RestaurantStaffCreateDto(UUID.randomUUID(), "owner")))
            .isInstanceOf(NotFoundException.class);
        when(restaurantRepository.findById(rid)).thenReturn(Optional.of(restaurant(rid)));
        assertThat(service.addStaff(rid, new RestaurantStaffCreateDto(UUID.randomUUID(), "owner"))).isNotNull();
    }

    @Test
    void patchStaff_notFound_roleChange_reactivate_deactivate() {
        when(staffRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patchStaff(UUID.randomUUID(), new RestaurantStaffPatchDto("owner", null, null, null, null)))
            .isInstanceOf(NotFoundException.class);

        RestaurantStaff active = staff(UUID.randomUUID(), UUID.randomUUID(), "manager");
        when(staffRepository.findById(active.getId())).thenReturn(Optional.of(active));
        service.patchStaff(active.getId(), new RestaurantStaffPatchDto("owner", false, null, null, null));
        assertThat(active.isDeleted()).isTrue();

        RestaurantStaff deleted = staff(UUID.randomUUID(), UUID.randomUUID(), "manager");
        deleted.markDeleted();
        when(staffRepository.findById(deleted.getId())).thenReturn(Optional.of(deleted));
        service.patchStaff(deleted.getId(), new RestaurantStaffPatchDto("  ", true, null, null, null));
        assertThat(deleted.isDeleted()).isFalse();
    }

    @Test
    void patchStaff_profileFields_updatesLinkedUser() {
        // Sprint M — l'owner (UPDATE:STAFF) édite nom/tél du membre → user lié mis à jour.
        RestaurantStaff s = staff(UUID.randomUUID(), UUID.randomUUID(), "manager");
        when(staffRepository.findById(s.getId())).thenReturn(Optional.of(s));
        service.patchStaff(s.getId(), new RestaurantStaffPatchDto(null, null, "Nouveau", "Nom", null));
        assertThat(s.getUser().getFirstName()).isEqualTo("Nouveau");
        assertThat(s.getUser().getLastName()).isEqualTo("Nom");
        verify(userRepo).save(s.getUser());
    }

    @Test
    void deleteStaff_notFoundAndSuccess() {
        when(staffRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteStaff(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        RestaurantStaff s = staff(UUID.randomUUID(), UUID.randomUUID(), "owner");
        when(staffRepository.findById(s.getId())).thenReturn(Optional.of(s));
        service.deleteStaff(s.getId());
        assertThat(s.isDeleted()).isTrue();
    }

    @Test
    void transferStaff_sameSourceTarget_throwsBadRequest() {
        UUID rid = UUID.randomUUID();
        assertThatThrownBy(() -> service.transferStaff(UUID.randomUUID(), rid, rid)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void transferStaff_staffNotFound_throwsNotFound() {
        when(staffRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.transferStaff(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void transferStaff_restaurantMismatch_throwsBadRequest() {
        UUID source = UUID.randomUUID(), target = UUID.randomUUID();
        RestaurantStaff s = staff(UUID.randomUUID(), UUID.randomUUID(), "owner"); // restaurant id ≠ source
        when(staffRepository.findById(s.getId())).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.transferStaff(s.getId(), source, target)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void transferStaff_alreadyStaffOnTarget_throwsConflict() {
        UUID source = UUID.randomUUID(), target = UUID.randomUUID(), uid = UUID.randomUUID();
        RestaurantStaff s = staff(source, uid, "owner");
        when(staffRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(restaurantRepository.findById(target)).thenReturn(Optional.of(restaurant(target)));
        when(staffRepository.findAllByRestaurantId(target)).thenReturn(List.of(staff(target, uid, "owner")));
        assertThatThrownBy(() -> service.transferStaff(s.getId(), source, target)).isInstanceOf(ConflictException.class);
    }

    @Test
    void transferStaff_success() {
        UUID source = UUID.randomUUID(), target = UUID.randomUUID(), uid = UUID.randomUUID();
        RestaurantStaff s = staff(source, uid, "owner");
        when(staffRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(restaurantRepository.findById(target)).thenReturn(Optional.of(restaurant(target)));
        when(staffRepository.findAllByRestaurantId(target)).thenReturn(List.of());
        assertThat(service.transferStaff(s.getId(), source, target)).isNotNull();
        assertThat(s.isDeleted()).isTrue();
    }

    @Test
    void inviteStaff_noEmailNoPhone_throwsBadRequest() {
        assertThatThrownBy(() -> service.inviteStaff(
            new InviteDto(UUID.randomUUID(), null, null, "owner", null), userRepo))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void inviteStaff_userNotFound_returnsUserExistsFalse() {
        when(userRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(userRepo.findByPhone(any())).thenReturn(Optional.empty());
        var res = service.inviteStaff(new InviteDto(UUID.randomUUID(), "x@y.ma", "0600", "owner", null), userRepo);
        assertThat(res.userExists()).isFalse();
    }

    @Test
    void inviteStaff_userFound_alreadyStaff_throwsConflict() {
        UUID rid = UUID.randomUUID(), uid = UUID.randomUUID();
        when(userRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.of(user(uid)));
        when(staffRepository.findAllByRestaurantId(rid)).thenReturn(List.of(staff(rid, uid, "owner")));
        assertThatThrownBy(() -> service.inviteStaff(new InviteDto(rid, "x@y.ma", null, "owner", null), userRepo))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void inviteStaff_userFound_success() {
        UUID rid = UUID.randomUUID(), uid = UUID.randomUUID();
        when(userRepo.findByEmailIgnoreCase(any())).thenReturn(Optional.of(user(uid)));
        when(staffRepository.findAllByRestaurantId(rid)).thenReturn(List.of());
        when(restaurantRepository.findById(rid)).thenReturn(Optional.of(restaurant(rid)));
        var res = service.inviteStaff(new InviteDto(rid, "x@y.ma", null, "owner", null), userRepo);
        assertThat(res.userExists()).isTrue();
    }

    // ─── MealServices ──────────────────────────────────────────────────────────

    @Test
    void services_list_add_validation() {
        when(mealServiceRepository.findAllByRestaurantId(any())).thenReturn(List.of(meal()));
        assertThat(service.listServices(UUID.randomUUID())).hasSize(1);

        UUID rid = UUID.randomUUID();
        when(restaurantRepository.findById(rid)).thenReturn(Optional.of(restaurant(rid)));
        assertThat(service.addService(rid, new MealServiceCreateDto("Dîner", LocalTime.of(19, 0), LocalTime.of(23, 0)))).isNotNull();
        assertThatThrownBy(() -> service.addService(rid, new MealServiceCreateDto("Bug", LocalTime.of(20, 0), LocalTime.of(19, 0))))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void patchService_notFound_success_invalidRange() {
        when(mealServiceRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patchService(UUID.randomUUID(), new MealServicePatchDto("X", null, null)))
            .isInstanceOf(NotFoundException.class);

        MealService m = meal();
        when(mealServiceRepository.findById(m.getId())).thenReturn(Optional.of(m));
        service.patchService(m.getId(), new MealServicePatchDto("Brunch", LocalTime.of(10, 0), LocalTime.of(14, 0)));
        assertThat(m.getName()).isEqualTo("Brunch");

        MealService m2 = meal();
        when(mealServiceRepository.findById(m2.getId())).thenReturn(Optional.of(m2));
        assertThatThrownBy(() -> service.patchService(m2.getId(), new MealServicePatchDto("  ", LocalTime.of(16, 0), LocalTime.of(15, 0))))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteService_notFoundAndSuccess() {
        when(mealServiceRepository.existsById(any())).thenReturn(false);
        assertThatThrownBy(() -> service.deleteService(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        UUID id = UUID.randomUUID();
        when(mealServiceRepository.existsById(id)).thenReturn(true);
        service.deleteService(id);
        verify(mealServiceRepository).deleteById(id);
    }

    // ─── Zones ─────────────────────────────────────────────────────────────────

    @Test
    void zones_list_add_delete() {
        when(zoneRepository.findAllByRestaurantId(any())).thenReturn(List.of(zone(UUID.randomUUID())));
        assertThat(service.listZones(UUID.randomUUID())).hasSize(1);

        UUID rid = UUID.randomUUID();
        when(restaurantRepository.findById(rid)).thenReturn(Optional.of(restaurant(rid)));
        assertThat(service.addZone(rid, new RestaurantZoneCreateDto("Salle"))).isNotNull();

        when(zoneRepository.existsById(any())).thenReturn(false);
        assertThatThrownBy(() -> service.deleteZone(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        UUID zid = UUID.randomUUID();
        when(zoneRepository.existsById(zid)).thenReturn(true);
        service.deleteZone(zid);
        verify(zoneRepository).deleteById(zid);
    }

    // ─── Tables ──────────────────────────────────────────────────────────────

    @Test
    void tables_list_addGuards_delete() {
        when(tableRepository.findAllByRestaurantId(any())).thenReturn(List.of(
            new RestaurantTable(UUID.randomUUID(), zone(UUID.randomUUID()), "T1", 4)));
        assertThat(service.listTables(UUID.randomUUID())).hasSize(1);

        UUID rid = UUID.randomUUID();
        // zone introuvable
        when(zoneRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addTable(rid, new RestaurantTableCreateDto(UUID.randomUUID(), "T2", 2)))
            .isInstanceOf(NotFoundException.class);

        // zone d'un autre resto → BadRequest
        RestaurantZone otherZone = zone(UUID.randomUUID());
        when(zoneRepository.findById(otherZone.getId())).thenReturn(Optional.of(otherZone));
        assertThatThrownBy(() -> service.addTable(rid, new RestaurantTableCreateDto(otherZone.getId(), "T3", 2)))
            .isInstanceOf(BadRequestException.class);

        // zone du bon resto → success
        RestaurantZone okZone = zone(rid);
        when(zoneRepository.findById(okZone.getId())).thenReturn(Optional.of(okZone));
        assertThat(service.addTable(rid, new RestaurantTableCreateDto(okZone.getId(), "T4", 6))).isNotNull();

        when(tableRepository.existsById(any())).thenReturn(false);
        assertThatThrownBy(() -> service.deleteTable(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        UUID tid = UUID.randomUUID();
        when(tableRepository.existsById(tid)).thenReturn(true);
        service.deleteTable(tid);
        verify(tableRepository).deleteById(tid);
    }
}
