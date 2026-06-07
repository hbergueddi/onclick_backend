package com.onesley.oneclick.core.identity.internal;

import com.onesley.oneclick.core.identity.api.Action;
import com.onesley.oneclick.core.identity.api.Menu;
import com.onesley.oneclick.core.identity.api.Permission;
import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserCreateDto;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.core.identity.api.UserUpdateDto;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.OneClickUserDetailsService;
import com.onesley.oneclick.security.SecurityHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link UserService} (L3 — core.identity).
 * Signup (conflits email/phone/role), lookups, patch, soft-delete + eviction,
 * changePassword (validations), rolesDistribution (native), findMe / findMeContext.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock UserRepository repository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock OneClickUserDetailsService userDetailsService;
    @Mock EntityManager em;
    @Mock Query query;
    @InjectMocks UserService service;

    private final Role role = new Role(UUID.randomUUID(), "CLIENT", "Client");

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "entityManager", em);
        lenient().when(em.getReference(eq(Tenant.class), any())).thenReturn(new Tenant(UUID.randomUUID(), "T", "t"));
        lenient().when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hashed");
    }

    private User user() { return new User(UUID.randomUUID(), role, "u@x.ma", "h", "U", "U"); }

    // ─── lookups ────────────────────────────────────────────────────────────

    @Test
    void findById_notFoundAndFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        assertThat(service.findById(u.getId())).isNotNull();
    }

    @Test
    void findByEmail_phone_referral_notFoundAndFound() {
        when(repository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findByEmail("x@y.ma")).isInstanceOf(NotFoundException.class);
        when(repository.findByEmailIgnoreCase(any())).thenReturn(Optional.of(user()));
        assertThat(service.findByEmail("x@y.ma")).isNotNull();

        when(repository.findByPhone(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findByPhone("0600")).isInstanceOf(NotFoundException.class);
        when(repository.findByPhone(any())).thenReturn(Optional.of(user()));
        assertThat(service.findByPhone("0600")).isNotNull();

        when(repository.findByReferralCode(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findByReferralCode("OC-1")).isInstanceOf(NotFoundException.class);
        when(repository.findByReferralCode(any())).thenReturn(Optional.of(user()));
        assertThat(service.findByReferralCode("OC-1")).isNotNull();
    }

    @Test
    void findAll_findByRole_delegate() {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAll(0, 20).getContent()).isEmpty();
        when(repository.findByRoleCode(any(), any(), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findByRole("CLIENT", UUID.randomUUID(), 0, 20).getContent()).isEmpty();
    }

    @Test
    void findAllByIds_emptyAndNonEmpty() {
        assertThat(service.findAllByIds(null)).isEmpty();
        assertThat(service.findAllByIds(List.of())).isEmpty();
        when(repository.findAllByIds(any())).thenReturn(List.of(user()));
        assertThat(service.findAllByIds(List.of(UUID.randomUUID()))).hasSize(1);
    }

    // ─── create ────────────────────────────────────────────────────────────

    @Test
    void create_emailConflict() {
        when(repository.existsByEmailIgnoreCase(any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(new UserCreateDto(null, role.getId(), "dup@x.ma", null, "password1", "F", "L", null)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_phoneConflict() {
        when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(repository.existsByPhone(any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(new UserCreateDto(null, role.getId(), "x@x.ma", "0600", "password1", "F", "L", null)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_roleNotFound() {
        when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(roleRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(new UserCreateDto(null, UUID.randomUUID(), "x@x.ma", null, "password1", "F", "L", null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_fullAndMinimal() {
        when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(repository.existsByPhone(any())).thenReturn(false);
        when(roleRepository.findById(any())).thenReturn(Optional.of(role));
        assertThat(service.create(new UserCreateDto(UUID.randomUUID(), role.getId(), "Full@x.ma", "0600", "password1", "F", "L", "fr"))).isNotNull();
        assertThat(service.create(new UserCreateDto(null, role.getId(), "Min@x.ma", null, "password1", "F", "L", null))).isNotNull();
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(any(Object.class));
    }

    // ─── patch ────────────────────────────────────────────────────────────

    @Test
    void patch_notFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patch(UUID.randomUUID(), new UserUpdateDto("F", null, null, null, null, null, null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void patch_allFields_success() {
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(repository.existsByPhone(any())).thenReturn(false);
        service.patch(u.getId(), new UserUpdateDto("Nouveau", "Nom", "0700", "http://avatar", "Casablanca", "en",
            List.of("gluten", "lactose")));
        assertThat(u.getFirstName()).isEqualTo("Nouveau");
        assertThat(u.getPhone()).isEqualTo("0700");
        assertThat(u.getCity()).isEqualTo("Casablanca"); // ITEM 1 — city persisté via patch self-service
        // V65 — allergènes persistés via patch self-service : List<String> → String[].
        assertThat(u.getAllergens()).containsExactly("gluten", "lactose");
    }

    /**
     * V65 — patch allergens : la liste fournie écrase le tableau de l'entité.
     * Cas isolé (sans toucher aux autres champs) pour prouver l'indépendance du champ.
     */
    @Test
    void patch_allergens_setsArray() {
        User u = user();
        u.setAllergens(new String[]{"eggs"}); // valeur initiale
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        service.patch(u.getId(), new UserUpdateDto(null, null, null, null, null, null,
            List.of("peanuts", "soy", "fish")));
        assertThat(u.getAllergens()).containsExactly("peanuts", "soy", "fish");
    }

    /**
     * V65 — patch SANS allergens (null) : le tableau existant reste inchangé
     * (même garde {@code if (dto.allergens() != null)} que pour city/language).
     */
    @Test
    void patch_allergensNull_leavesArrayUnchanged() {
        User u = user();
        u.setAllergens(new String[]{"celery", "mustard"});
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(repository.existsByPhone(any())).thenReturn(false);
        service.patch(u.getId(), new UserUpdateDto("OnlyName", null, null, null, null, null, null));
        assertThat(u.getFirstName()).isEqualTo("OnlyName");
        assertThat(u.getAllergens()).containsExactly("celery", "mustard"); // inchangé
    }

    // ─── H (V79) — updatePccMemberType ───────────────────────────────────────

    @Test
    void updatePccMemberType_resident_sets() {
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        var dto = service.updatePccMemberType(u.getId(), "resident");
        assertThat(u.getPccMemberType()).isEqualTo("resident");
        assertThat(dto.pccMemberType()).isEqualTo("resident");
    }

    @Test
    void updatePccMemberType_null_clears() {
        User u = user();
        u.setPccMemberType("non_resident");
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        service.updatePccMemberType(u.getId(), null);
        assertThat(u.getPccMemberType()).isNull();
    }

    @Test
    void updatePccMemberType_invalid_throwsBadRequest_noLookup() {
        assertThatThrownBy(() -> service.updatePccMemberType(UUID.randomUUID(), "vip"))
            .isInstanceOf(BadRequestException.class);
        verify(repository, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void updatePccMemberType_notFound_throws() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updatePccMemberType(UUID.randomUUID(), "resident"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void patch_phoneConflict() {
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(repository.existsByPhone(any())).thenReturn(true);
        assertThatThrownBy(() -> service.patch(u.getId(), new UserUpdateDto(null, null, "0700", null, null, null, null)))
            .isInstanceOf(ConflictException.class);
    }

    // ─── softDelete ──────────────────────────────────────────────────────────

    @Test
    void softDelete_notFoundAndSuccess() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDelete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        service.softDelete(u.getId());
        assertThat(u.getDeletedAt()).isNotNull();
        verify(userDetailsService).evictUser(u.getId());
    }

    // ─── changePassword ──────────────────────────────────────────────────────

    @Test
    void changePassword_notFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changePassword(UUID.randomUUID(), "old", "new"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void changePassword_currentInvalid() {
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("wrong"), any())).thenReturn(false);
        assertThatThrownBy(() -> service.changePassword(u.getId(), "wrong", "newpass"))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void changePassword_sameAsOld() {
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("current"), any())).thenReturn(true);
        when(passwordEncoder.matches(eq("current2"), any())).thenReturn(true);
        assertThatThrownBy(() -> service.changePassword(u.getId(), "current", "current2"))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void changePassword_success_evicts() {
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("current"), any())).thenReturn(true);
        when(passwordEncoder.matches(eq("brandnew"), any())).thenReturn(false);
        service.changePassword(u.getId(), "current", "brandnew");
        verify(userDetailsService).evictUser(u.getId());
    }

    // ─── rolesDistribution (native) ────────────────────────────────────────────

    @Test
    void rolesDistribution_mapsRows() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(new Object[]{"CLIENT", 100L}, new Object[]{"STAFF", 12L}));
        assertThat(service.rolesDistribution()).hasSize(2);
    }

    // ─── findMe / findMeContext ────────────────────────────────────────────────

    @Test
    void findMe_notAuthenticated_andAuthenticated() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThatThrownBy(() -> service.findMe()).isInstanceOf(BadRequestException.class);
        }
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(u.getId());
            assertThat(service.findMe()).isNotNull();
        }
    }

    @Test
    void findMeContext_notAuthenticated_userNotFound_success() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThatThrownBy(() -> service.findMeContext()).isInstanceOf(BadRequestException.class);
        }
        UUID id = UUID.randomUUID();
        when(repository.findByIdWithRoleAndPermissions(id)).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(id);
            assertThatThrownBy(() -> service.findMeContext()).isInstanceOf(NotFoundException.class);
        }
        // succès : user avec rôle + permissions (menu/action) → menus + permissions construits
        Menu menu = new Menu(UUID.randomUUID(), "dashboard", "Dashboard");
        Action action = new Action(UUID.randomUUID(), "VIEW", "Voir", "core");
        role.getPermissions().add(new Permission(UUID.randomUUID(), role, menu, action));
        User u = new User(UUID.randomUUID(), role, "me@x.ma", "h", "F", "L");
        when(repository.findByIdWithRoleAndPermissions(u.getId())).thenReturn(Optional.of(u));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(u.getId());
            var ctx = service.findMeContext();
            assertThat(ctx.menus()).hasSize(1);
            assertThat(ctx.permissions()).contains("VIEW:dashboard");
        }
    }
}
