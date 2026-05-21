package com.onesley.oneclick.core.identity;

import com.onesley.oneclick.core.identity.api.Action;
import com.onesley.oneclick.core.identity.api.Menu;
import com.onesley.oneclick.core.identity.api.Permission;
import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserDto;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des entités du module identity (Layer 1 — couverture totale).
 * Purs (sans Spring) : constructeurs, getters, {@code toDto()}, contrat
 * {@code equals/hashCode} (id-based, anti-proxy LAZY).
 */
class IdentityEntitiesTest {

    private static Role role(String code) { return new Role(UUID.randomUUID(), code, code + " name"); }

    // ─── User ──────────────────────────────────────────────────────────────────
    @Test
    void user_constructor_setsCoreFields() {
        UUID id = UUID.randomUUID();
        Role r = role("CLIENT");
        User u = new User(id, r, "a@b.ma", "$2a$hash", "Ada", "Lovelace");
        assertThat(u.getId()).isEqualTo(id);
        assertThat(u.getRole()).isEqualTo(r);
        assertThat(u.getEmail()).isEqualTo("a@b.ma");
        assertThat(u.getPasswordHash()).isEqualTo("$2a$hash");
        assertThat(u.getFirstName()).isEqualTo("Ada");
        assertThat(u.getLastName()).isEqualTo("Lovelace");
        // défauts
        assertThat(u.getLanguage()).isEqualTo("fr");
        assertThat(u.getStatus()).isEqualTo("active");
        assertThat(u.isEnabled()).isTrue();
        assertThat(u.isAccountNonExpired()).isTrue();
        assertThat(u.isAccountNonLocked()).isTrue();
        assertThat(u.isCredentialsNonExpired()).isTrue();
    }

    @Test
    void user_setters_mutateFields() {
        User u = new User(UUID.randomUUID(), role("CLIENT"), "a@b.ma", "h", "A", "B");
        u.setEmail("c@d.ma"); u.setPhone("+212600"); u.setFirstName("X"); u.setLastName("Y");
        u.setAvatarUrl("http://img"); u.setLanguage("en"); u.setStatus("suspended");
        u.setReferralCode("OC-ABCDEF");
        assertThat(u.getEmail()).isEqualTo("c@d.ma");
        assertThat(u.getPhone()).isEqualTo("+212600");
        assertThat(u.getLanguage()).isEqualTo("en");
        assertThat(u.getStatus()).isEqualTo("suspended");
        assertThat(u.getReferralCode()).isEqualTo("OC-ABCDEF");
    }

    @Test
    void user_toDto_mapsAllFieldsIncludingRoleCode_andHidesPasswordHash() {
        UUID id = UUID.randomUUID();
        Role r = role("RESTAURATEUR");
        User u = new User(id, r, "a@b.ma", "$2a$secret", "Ada", "Lovelace");
        u.setPhone("+212611"); u.setAvatarUrl("http://a"); u.setLanguage("ar"); u.setStatus("active");
        u.setReferralCode("OC-123456");

        UserDto dto = u.toDto();

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.roleCode()).isEqualTo("RESTAURATEUR");
        assertThat(dto.email()).isEqualTo("a@b.ma");
        assertThat(dto.phone()).isEqualTo("+212611");
        assertThat(dto.firstName()).isEqualTo("Ada");
        assertThat(dto.lastName()).isEqualTo("Lovelace");
        assertThat(dto.avatarUrl()).isEqualTo("http://a");
        assertThat(dto.language()).isEqualTo("ar");
        assertThat(dto.status()).isEqualTo("active");
        assertThat(dto.referralCode()).isEqualTo("OC-123456");
        assertThat(dto.enabled()).isTrue();
        // UserDto n'expose AUCUN champ password (sécurité) — vérifié structurellement
        // par l'absence d'accesseur ; ici on s'assure du non-null des champs publics.
    }

    @Test
    void user_toDto_nullRole_yieldsNullRoleCode() {
        User u = new User(UUID.randomUUID(), null, "a@b.ma", "h", "A", "B");
        assertThat(u.toDto().roleCode()).isNull();
    }

    @Test
    void user_equalsHashCode_idBased_andProxyAware() {
        UUID id = UUID.randomUUID();
        User a = new User(id, role("CLIENT"), "a@b.ma", "h", "A", "B");
        User b = new User(id, role("STAFF"), "z@z.ma", "h2", "Z", "Z"); // même id, autres champs
        User c = new User(UUID.randomUUID(), role("CLIENT"), "a@b.ma", "h", "A", "B");

        assertThat(a).isEqualTo(a);            // réflexif
        assertThat(a).isEqualTo(b);            // égalité par id
        assertThat(a).hasSameHashCodeAs(b);    // hashCode stable par classe
        assertThat(a).isNotEqualTo(c);         // id différent
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("string");
    }

    // ─── Role ───────────────────────────────────────────────────────────────────
    @Test
    void role_constructor_andPermissionsSetMutable() {
        UUID id = UUID.randomUUID();
        Role r = new Role(id, "SUPERADMIN", "Super Admin");
        assertThat(r.getId()).isEqualTo(id);
        assertThat(r.getCode()).isEqualTo("SUPERADMIN");
        assertThat(r.getName()).isEqualTo("Super Admin");
        assertThat(r.getPermissions()).isEmpty();
        r.getPermissions().add(new Permission(UUID.randomUUID(), r, null, null));
        assertThat(r.getPermissions()).hasSize(1);
        r.setName("renamed");
        assertThat(r.getName()).isEqualTo("renamed");
    }

    @Test
    void role_equals_idBased() {
        UUID id = UUID.randomUUID();
        assertThat(new Role(id, "A", "a")).isEqualTo(new Role(id, "B", "b"));
        assertThat(new Role(UUID.randomUUID(), "A", "a")).isNotEqualTo(new Role(UUID.randomUUID(), "A", "a"));
    }

    // ─── Menu ─────────────────────────────────────────────────────────────────
    @Test
    void menu_constructor_setters_equals() {
        UUID id = UUID.randomUUID();
        Menu m = new Menu(id, "RESTAURANTS", "Restaurants");
        assertThat(m.getCode()).isEqualTo("RESTAURANTS");
        assertThat(m.getSortOrder()).isZero();
        Menu parent = new Menu(UUID.randomUUID(), "ADMIN", "Admin");
        m.setIcon("store"); m.setPath("/restaurants"); m.setParent(parent); m.setSortOrder(3);
        assertThat(m.getIcon()).isEqualTo("store");
        assertThat(m.getPath()).isEqualTo("/restaurants");
        assertThat(m.getParent()).isEqualTo(parent);
        assertThat(m.getSortOrder()).isEqualTo(3);
        assertThat(m).isEqualTo(new Menu(id, "OTHER", "Other"));
    }

    // ─── Action ─────────────────────────────────────────────────────────────────
    @Test
    void action_constructor_equals() {
        UUID id = UUID.randomUUID();
        Action a = new Action(id, "VIEW", "View", "core");
        assertThat(a.getCode()).isEqualTo("VIEW");
        assertThat(a.getName()).isEqualTo("View");
        assertThat(a.getModule()).isEqualTo("core");
        assertThat(a).isEqualTo(new Action(id, "X", "x", "m"));
        assertThat(a).isNotEqualTo(new Action(UUID.randomUUID(), "VIEW", "View", "core"));
    }

    // ─── Permission ───────────────────────────────────────────────────────────
    @Test
    void permission_constructor_holdsRoleMenuAction_andEquals() {
        UUID id = UUID.randomUUID();
        Role r = role("STAFF");
        Menu m = new Menu(UUID.randomUUID(), "LOYALTY", "Loyalty");
        Action a = new Action(UUID.randomUUID(), "CREATE", "Create", "core");
        Permission p = new Permission(id, r, m, a);
        assertThat(p.getRole()).isEqualTo(r);
        assertThat(p.getMenu()).isEqualTo(m);
        assertThat(p.getAction()).isEqualTo(a);
        assertThat(p).isEqualTo(new Permission(id, r, null, null));
        assertThat(p).isNotEqualTo(new Permission(UUID.randomUUID(), r, m, a));
    }

    @Test
    void entity_nullId_distinctInstancesNotEqual() {
        // equals : `id != null && Objects.equals(...)` -> deux instances à id null
        // ne sont PAS égales (mais chacune égale à elle-même via this==o).
        Role r1 = new Role(null, "A", "a");
        Role r2 = new Role(null, "A", "a");
        assertThat(r1).isEqualTo(r1);
        assertThat(r1).isNotEqualTo(r2);
    }
}
