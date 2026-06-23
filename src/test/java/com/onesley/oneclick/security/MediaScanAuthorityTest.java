package com.onesley.oneclick.security;

import com.onesley.oneclick.core.identity.api.Action;
import com.onesley.oneclick.core.identity.api.Menu;
import com.onesley.oneclick.core.identity.api.Permission;
import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test unitaire ISOLÉ (zéro Spring / zéro DB) du contrat d'autorité dont dépend la
 * migration {@code V101__grant_media_to_scanning_roles}. On construit en mémoire le
 * graphe {@code User → Role(RESTAURATEUR) → Permission(MEDIA, {UPLOAD|CREATE|VIEW})}
 * et on vérifie que {@link OneClickUserDetails#getAuthorities()} produit EXACTEMENT
 * les chaînes {@code UPLOAD:MEDIA / CREATE:MEDIA / VIEW:MEDIA} — celles que les
 * {@code @PreAuthorize("hasAuthority('UPLOAD:MEDIA')")} du {@code MediaController}
 * exigent. Garde-fou anti-régression : si le format {@code ACTION:MENU} dérivait,
 * le grant V101 n'autoriserait plus rien (le scan-photo recasserait silencieusement).
 *
 * <p>DELETE:MEDIA n'est PAS accordé par V101 (moindre privilège) : on vérifie aussi
 * son absence pour documenter la frontière.
 */
class MediaScanAuthorityTest {

    private static Action action(String code) { return new Action(UUID.randomUUID(), code, code, "core"); }
    private static Menu menu(String code) { return new Menu(UUID.randomUUID(), code, code); }

    /** Rôle "scanner" tel que V101 le laisse : MEDIA × {UPLOAD, CREATE, VIEW}. */
    private static Role restaurateurWithMediaGrants() {
        Role r = new Role(UUID.randomUUID(), "RESTAURATEUR", "Restaurateur");
        r.getPermissions().add(new Permission(UUID.randomUUID(), r, menu("MEDIA"), action("UPLOAD")));
        r.getPermissions().add(new Permission(UUID.randomUUID(), r, menu("MEDIA"), action("CREATE")));
        r.getPermissions().add(new Permission(UUID.randomUUID(), r, menu("MEDIA"), action("VIEW")));
        // + la capacité de scan elle-même (déjà en place avant V101)
        r.getPermissions().add(new Permission(UUID.randomUUID(), r, menu("LOYALTY"), action("CREATE")));
        return r;
    }

    private static List<String> authStrings(Role r) {
        User u = new User(UUID.randomUUID(), r, "owner@oneclick.ma", "$2a$10$x", "Owner", "Loliva");
        return OneClickUserDetails.from(u).getAuthorities().stream()
            .map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void grantedMediaPermissions_resolveToVerbResourceAuthorities() {
        List<String> auths = authStrings(restaurateurWithMediaGrants());

        // Les 3 autorités média que V101 octroie = exactement celles vérifiées par les @PreAuthorize.
        assertThat(auths).contains("UPLOAD:MEDIA", "CREATE:MEDIA", "VIEW:MEDIA");
        // La capacité de scan reste présente (non touchée par V101).
        assertThat(auths).contains("CREATE:LOYALTY");
    }

    @Test
    void deleteMedia_isNotGranted_leastPrivilege() {
        // V101 n'accorde PAS DELETE:MEDIA (suppression média = admin) → frontière documentée.
        assertThat(authStrings(restaurateurWithMediaGrants())).doesNotContain("DELETE:MEDIA");
    }
}
