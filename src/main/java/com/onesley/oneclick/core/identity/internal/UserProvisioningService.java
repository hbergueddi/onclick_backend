package com.onesley.oneclick.core.identity.internal;

import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserCreateDto;
import com.onesley.oneclick.core.identity.api.UserDto;
import com.onesley.oneclick.core.identity.api.UserProvisioningApi;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * BE-2 — implémentation de {@link UserProvisioningApi} (provisioning d'un compte gérant restaurateur
 * à l'approbation d'une demande d'inscription d'enseigne).
 *
 * <p>Réutilise {@link UserService#create} (gardes anti-doublon email/téléphone + encodage BCrypt +
 * publication {@code UserRegisteredEvent}), puis positionne {@code password_must_change=true} (BE-3)
 * sur l'entité fraîchement créée — le gérant devra définir son mot de passe au 1er login. Tout dans
 * la même transaction que l'appelant (frontière : l'appelant est un module métier, l'écriture reste
 * confinée à identity).
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
class UserProvisioningService implements UserProvisioningApi {

    /** Rôle applicatif du compte gérant créé (cf. seed roles V… — code stable). */
    private static final String OWNER_ROLE_CODE = "RESTAURATEUR";

    private final UserService userService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    @Override
    public UUID provisionOwner(ProvisionOwnerCommand cmd) {
        Role role = roleRepository.findByCode(OWNER_ROLE_CODE)
            .orElseThrow(() -> new NotFoundException("Role", OWNER_ROLE_CODE));

        // create() porte les gardes ConflictException (email/téléphone déjà pris) + BCrypt + event.
        UserDto created = userService.create(new UserCreateDto(
            cmd.tenantId(), role.getId(), cmd.email(), cmd.phone(),
            cmd.rawPassword(), cmd.firstName(), cmd.lastName(), "fr"));

        // BE-3 — force la saisie d'un nouveau mot de passe au 1er login (mdp temporaire).
        User u = userRepository.findById(created.id())
            .orElseThrow(() -> new NotFoundException("User", created.id()));
        u.setPasswordMustChange(true);
        userRepository.save(u);

        log.info("[user/provisioning] compte gérant créé user={} role={} (password_must_change=true)",
            created.id(), OWNER_ROLE_CODE);
        return created.id();
    }
}
