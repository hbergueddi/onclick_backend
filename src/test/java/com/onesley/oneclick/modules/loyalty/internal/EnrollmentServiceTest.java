package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.EnrollMemberDto;
import com.onesley.oneclick.modules.loyalty.api.EnrollMemberResultDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTransactionDto;
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
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link EnrollmentService} (L3 — modules.loyalty).
 * Orchestrateur "Inscrire membre" : RBAC (SecurityHelper statique), plafond
 * welcome_points, client existant vs création, requêtes natives (staff, tenant).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class EnrollmentServiceTest {

    @Mock UserRepository userRepository;
    @Mock GainRuleRepository gainRuleRepository;
    @Mock LoyaltyService loyaltyService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EntityManager em;
    @Mock Query query;
    @InjectMocks EnrollmentService service;

    private final UUID resto = UUID.randomUUID();
    private final UUID caller = UUID.randomUUID();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "em", em);
        // Chaîne native query : createNativeQuery -> setParameter (self) -> getSingleResult/getResultList
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
    }

    private EnrollMemberDto dto(UUID clientId, String email, String first, String last, int points) {
        return new EnrollMemberDto(resto, clientId, email, null, first, last, points, false);
    }
    private GainRule rule(int max) {
        GainRule r = new GainRule(UUID.randomUUID(), resto, new java.math.BigDecimal("0.10"));
        r.setWelcomePointsMax(max);
        return r;
    }
    private LoyaltyTransactionDto tx(int points) {
        return new LoyaltyTransactionDto(UUID.randomUUID(), UUID.randomUUID(), "earn", points,
            null, "welcome", null, null, null, null, null);
    }
    private MockedStatic<SecurityHelper> adminContext(MockedStatic<SecurityHelper> sec) {
        sec.when(SecurityHelper::currentUserId).thenReturn(caller);
        sec.when(() -> SecurityHelper.hasRole("SUPERADMIN")).thenReturn(true);
        return sec;
    }

    @Test
    void enroll_notAuthenticated_throwsForbidden() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            // currentUserId() -> null par défaut
            assertThatThrownBy(() -> service.enrollMember(dto(UUID.randomUUID(), null, null, null, 10)))
                .isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void enroll_notStaffNorAdmin_throwsForbidden() {
        when(query.getSingleResult()).thenReturn(0L); // isActiveStaff count = 0
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(caller);
            sec.when(() -> SecurityHelper.hasRole("SUPERADMIN")).thenReturn(false);
            assertThatThrownBy(() -> service.enrollMember(dto(UUID.randomUUID(), null, null, null, 10)))
                .isInstanceOf(ForbiddenException.class);
        }
    }

    // ─── lookupClient (flow recherche « Inscrire membre » staff, CREATE:LOYALTY) ───

    @Test
    void lookupClient_byEmail_found_returnsMinimalIdentity() {
        UUID uid = UUID.randomUUID();
        User u = new User(uid, null, "jean@x.com", "hash", "Jean", "Dupont");
        when(userRepository.findByEmailIgnoreCase("jean@x.com")).thenReturn(Optional.of(u));
        var dto = service.lookupClient("jean@x.com", null);
        assertThat(dto.id()).isEqualTo(uid);
        assertThat(dto.firstName()).isEqualTo("Jean");
        assertThat(dto.lastName()).isEqualTo("Dupont");
    }

    @Test
    void lookupClient_notFound_throwsNotFound() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.lookupClient("nobody@x.com", null))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void enroll_gainRuleNotFound_throwsNotFound() {
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            adminContext(sec);
            assertThatThrownBy(() -> service.enrollMember(dto(UUID.randomUUID(), null, null, null, 10)))
                .isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    void enroll_exceedsWelcomeCap_throwsBadRequest() {
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.of(rule(100)));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            adminContext(sec);
            assertThatThrownBy(() -> service.enrollMember(dto(UUID.randomUUID(), null, null, null, 200)))
                .isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void enroll_existingClientId_notFound_throwsNotFound() {
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.of(rule(500)));
        UUID client = UUID.randomUUID();
        when(userRepository.findById(client)).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            adminContext(sec);
            assertThatThrownBy(() -> service.enrollMember(dto(client, null, null, null, 50)))
                .isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    void enroll_existingClientId_success_credits() {
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.of(rule(500)));
        UUID client = UUID.randomUUID();
        when(userRepository.findById(client)).thenReturn(Optional.of(
            new User(client, null, "c@x.ma", "h", "C", "E")));
        when(loyaltyService.earnPoints(any())).thenReturn(tx(100));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            adminContext(sec);
            EnrollMemberResultDto r = service.enrollMember(dto(client, null, null, null, 100));
            assertThat(r.clientId()).isEqualTo(client);
            assertThat(r.isNewUser()).isFalse();
            assertThat(r.pointsGranted()).isEqualTo(100);
        }
    }

    @Test
    void enroll_welcomeZero_noTransaction() {
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.of(rule(500)));
        UUID client = UUID.randomUUID();
        when(userRepository.findById(client)).thenReturn(Optional.of(new User(client, null, "c@x.ma", "h", "C", "E")));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            adminContext(sec);
            EnrollMemberResultDto r = service.enrollMember(dto(client, null, null, null, 0));
            assertThat(r.pointsGranted()).isZero();
            assertThat(r.transactionId()).isNull();
        }
    }

    @Test
    void enroll_lookupExistingByEmail_usesIt() {
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.of(rule(500)));
        UUID existing = UUID.randomUUID();
        when(userRepository.findByEmailIgnoreCase("found@x.ma"))
            .thenReturn(Optional.of(new User(existing, null, "found@x.ma", "h", "F", "X")));
        when(loyaltyService.earnPoints(any())).thenReturn(tx(50));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            adminContext(sec);
            EnrollMemberResultDto r = service.enrollMember(dto(null, "found@x.ma", null, null, 50));
            assertThat(r.clientId()).isEqualTo(existing);
            assertThat(r.isNewUser()).isFalse();
        }
    }

    @Test
    void enroll_createNewUser_emailBlank_throwsBadRequest() {
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.of(rule(500)));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            adminContext(sec);
            // clientId null, email null/blank -> lookup vide -> createClientUser -> email requis
            assertThatThrownBy(() -> service.enrollMember(dto(null, null, null, null, 10)))
                .isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void enroll_createNewUser_namesBlank_throwsBadRequest() {
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.of(rule(500)));
        when(userRepository.findByEmailIgnoreCase("new@x.ma")).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            adminContext(sec);
            assertThatThrownBy(() -> service.enrollMember(dto(null, "new@x.ma", null, null, 10)))
                .isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void enroll_createNewUser_success() {
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.of(rule(500)));
        when(userRepository.findByEmailIgnoreCase("new@x.ma")).thenReturn(Optional.empty());
        when(query.getSingleResult()).thenReturn(UUID.randomUUID()); // tenant_id du resto
        when(em.getReference(eq(Role.class), any())).thenReturn(new Role(UUID.randomUUID(), "CLIENT", "Client"));
        when(em.getReference(eq(Tenant.class), any())).thenReturn(new Tenant(UUID.randomUUID(), "T", "t"));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(loyaltyService.earnPoints(any())).thenReturn(tx(50));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            adminContext(sec);
            EnrollMemberResultDto r = service.enrollMember(dto(null, "new@x.ma", "New", "Member", 50));
            assertThat(r.isNewUser()).isTrue();
            assertThat(r.pointsGranted()).isEqualTo(50);
        }
    }

    @Test
    void listRecentEnrollments_forbidden_whenNotStaff() {
        when(query.getSingleResult()).thenReturn(0L);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(caller);
            sec.when(() -> SecurityHelper.hasRole("SUPERADMIN")).thenReturn(false);
            assertThatThrownBy(() -> service.listRecentEnrollments(resto, 10)).isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void listRecentEnrollments_success_mapsRows() {
        Object[] row = { UUID.randomUUID(), UUID.randomUUID(), "Ada", "L", "a@x.ma", 100, java.time.Instant.now() };
        when(query.getResultList()).thenReturn(java.util.Collections.singletonList(row));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            adminContext(sec);
            assertThat(service.listRecentEnrollments(resto, 10)).hasSize(1);
        }
    }
}
