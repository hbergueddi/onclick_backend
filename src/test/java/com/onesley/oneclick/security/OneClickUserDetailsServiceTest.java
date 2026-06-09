package com.onesley.oneclick.security;

import com.onesley.oneclick.core.identity.api.Action;
import com.onesley.oneclick.core.identity.api.Menu;
import com.onesley.oneclick.core.identity.api.Permission;
import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link OneClickUserDetailsService} (L3 — security).
 * loadUserByUsername : null/blank, UUID invalide, user introuvable, succès (touch graphe RBAC).
 * evictUser / evictAll : hooks no-op (cache annotation-driven, inerte hors contexte Spring).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class OneClickUserDetailsServiceTest {

    @Mock UserRepository userRepository;
    @Mock MembershipDirectoryApi membershipDirectory; // P1 : pliage des authorities — wiré via @InjectMocks
    @InjectMocks OneClickUserDetailsService service;

    @Test
    void loadUserByUsername_blank_throws() {
        assertThatThrownBy(() -> service.loadUserByUsername(null)).isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> service.loadUserByUsername("  ")).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsername_invalidUuid_throws() {
        assertThatThrownBy(() -> service.loadUserByUsername("pas-un-uuid")).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsername_userNotFound_throws() {
        when(userRepository.findByIdWithRoleAndPermissions(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadUserByUsername(UUID.randomUUID().toString()))
            .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsername_success_buildsUserDetails() {
        Role role = new Role(UUID.randomUUID(), "CLIENT", "Client");
        Menu menu = new Menu(UUID.randomUUID(), "dashboard", "Dashboard");
        Action action = new Action(UUID.randomUUID(), "VIEW", "Voir", "core");
        role.getPermissions().add(new Permission(UUID.randomUUID(), role, menu, action));
        User u = new User(UUID.randomUUID(), role, "me@x.ma", "h", "F", "L");
        when(userRepository.findByIdWithRoleAndPermissions(u.getId())).thenReturn(Optional.of(u));

        UserDetails details = service.loadUserByUsername(u.getId().toString());
        assertThat(details).isNotNull();
        assertThat(details.getUsername()).isNotNull();
    }

    @Test
    void evictHooks_noop() {
        service.evictUser(UUID.randomUUID());
        service.evictAll();
    }
}
