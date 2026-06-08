package com.onesley.oneclick.core.membership.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.core.membership.api.InviteMemberDto;
import com.onesley.oneclick.core.membership.api.MembershipDto;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.shared.events.MemberEnrollmentRequestedEvent;
import com.onesley.oneclick.shared.events.MembershipActivatedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit isolé (Mockito) de {@link MembershipInviteService} — P2.
 * Couvre l'auth + l'ABAC own-tenant + l'idempotence + la réactivation + la logique d'events.
 * Le chemin « création de compte » (em.getReference Role/Tenant) est couvert par l'intégration.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipInviteServiceTest {

    @Mock MembershipRepository membershipRepository;
    @Mock UserRepository userRepository;
    @Mock UserDirectoryApi userDirectory;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher events;
    @Mock EntityManager em;
    @Mock Query query;
    @InjectMocks MembershipInviteService service;

    private static final UUID PALMERAIE = UUID.fromString("0cccc000-0000-4000-8000-000000000001");
    private static final UUID MEMBER_ROLE = UUID.fromString("10000000-0000-0000-0000-000000000006");
    private final UUID caller = UUID.randomUUID();

    @BeforeEach
    void injectEntityManager() {
        // @InjectMocks utilise le constructeur Lombok (champs final) et n'injecte donc PAS le
        // champ @PersistenceContext em → on le pose à la main.
        ReflectionTestUtils.setField(service, "em", em);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UUID userId, String... authorities) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none")
            .subject(userId.toString()).build();
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, granted));
    }

    private InviteMemberDto dto(String email) {
        return new InviteMemberDto(email, null, "First", "Last", null);
    }

    private void stubResolveTenant() {
        when(em.createNativeQuery("SELECT slug, name FROM tenants WHERE id = :id")).thenReturn(query);
        when(query.setParameter(eq("id"), any())).thenReturn(query);
        // NB: pas List.of(new Object[]{...}) — varargs → List<String> de 2 élts (piège). Liste explicite.
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        rows.add(new Object[]{"palmeraie", "PCC"});
        when(query.getResultList()).thenReturn(rows);
    }

    @Test
    void notAuthenticated_throwsForbidden() {
        assertThatThrownBy(() -> service.invite(PALMERAIE, dto("a@b.test")))
            .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(membershipRepository, userRepository, events);
    }

    @Test
    void crossTenant_nonPlatformAdmin_throwsForbidden() {
        authenticate(caller, "CREATE:MEMBERSHIPS");
        when(userDirectory.tenantIdById(caller)).thenReturn(Optional.of(UUID.randomUUID())); // autre tenant home
        assertThatThrownBy(() -> service.invite(PALMERAIE, dto("a@b.test")))
            .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(membershipRepository, events);
    }

    @Test
    void alreadyActiveMember_isIdempotent_noSave_noEvents() {
        authenticate(caller, "CREATE:MEMBERSHIPS", "DELETE:TENANTS"); // plateforme → ABAC bypass
        stubResolveTenant();
        UUID uid = UUID.randomUUID();
        User existing = mock(User.class);
        when(existing.getId()).thenReturn(uid);
        when(existing.getDeletedAt()).thenReturn(null);
        when(userRepository.findByEmailIgnoreCase("a@b.test")).thenReturn(Optional.of(existing));
        TenantMembership active = mock(TenantMembership.class);
        when(active.getStatus()).thenReturn(TenantMembership.STATUS_ACTIVE);
        when(active.getId()).thenReturn(UUID.randomUUID());
        when(membershipRepository.findByUserIdAndTenantIdAndDeletedAtIsNull(uid, PALMERAIE))
            .thenReturn(Optional.of(active));

        MembershipDto out = service.invite(PALMERAIE, dto("a@b.test"));

        assertThat(out.newAccount()).isFalse();
        assertThat(out.status()).isEqualTo(TenantMembership.STATUS_ACTIVE);
        verify(membershipRepository, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void existingUser_revokedMembership_reactivated_evictionEventOnly() {
        authenticate(caller, "CREATE:MEMBERSHIPS", "DELETE:TENANTS");
        stubResolveTenant();
        UUID uid = UUID.randomUUID();
        User existing = mock(User.class);
        when(existing.getId()).thenReturn(uid);
        when(existing.getDeletedAt()).thenReturn(null);
        when(userRepository.findByEmailIgnoreCase("a@b.test")).thenReturn(Optional.of(existing));
        TenantMembership revoked = mock(TenantMembership.class);
        when(revoked.getStatus()).thenReturn(TenantMembership.STATUS_REVOKED);
        when(revoked.getId()).thenReturn(UUID.randomUUID());
        when(revoked.getJoinedAt()).thenReturn(Instant.now());
        when(revoked.getRoleId()).thenReturn(MEMBER_ROLE);
        when(membershipRepository.findByUserIdAndTenantIdAndDeletedAtIsNull(uid, PALMERAIE))
            .thenReturn(Optional.of(revoked));

        service.invite(PALMERAIE, dto("a@b.test"));

        verify(revoked).setStatus(TenantMembership.STATUS_ACTIVE);
        verify(membershipRepository).save(revoked);
        // compte existant → PAS d'email d'activation ; éviction de cache OUI
        verify(events).publishEvent(any(MembershipActivatedEvent.class));
        verify(events, never()).publishEvent(any(MemberEnrollmentRequestedEvent.class));
    }
}
