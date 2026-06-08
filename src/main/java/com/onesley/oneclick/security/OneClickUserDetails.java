package com.onesley.oneclick.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.onesley.oneclick.core.identity.api.Permission;
import com.onesley.oneclick.core.identity.api.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.Getter;

/**
 * {@link UserDetails} OneClick — enveloppe le {@link User} complet.
 *
 * <p>Refonte (retour senior) : « c'est comme {@code User}, on enlève que le mot
 * de passe, même chose — et c'est ici qu'on lie le rôle, le menu et les
 * permissions ». On tient donc l'entité {@code User} entière (profil : nom,
 * email, téléphone, langue, statut… + rôle + permissions + menus accessibles via
 * {@code user.getRole().getPermissions()}), au lieu d'aplatir en primitives.
 *
 * <p><strong>Mot de passe caché</strong> : {@link #getPassword()} renvoie
 * {@code null} (jamais consommé — le login vérifie le hash directement sur
 * l'entité {@code User} dans {@code AuthService}) et le champ {@code passwordHash}
 * est exclu de la sérialisation cache via un MixIn Jackson dédié (cf
 * {@code UserDetailsCacheConfig}).
 *
 * <p><strong>Cache Redis</strong> : sérialisé sous {@code userDetails} (TTL 1 h)
 * par un serializer dédié qui ignore les références non chargées / cycles
 * ({@code tenant}, {@code Permission.role} back-ref, {@code Menu.parent}). Le
 * graphe {@code role → permissions → menu/action} est chargé en JOIN FETCH par
 * {@code UserRepository.findByIdWithRoleAndPermissions} avant la mise en cache.
 *
 * <p>Les authorities ({@code ROLE_<code>}, {@code <code>}, {@code VERB:RESOURCE})
 * sont <em>calculées à la lecture</em> depuis le graphe tenu — donc non stockées,
 * rien à sérialiser pour elles.
 */
@Getter
public final class OneClickUserDetails implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 3L;

    /** L'entité {@code User} complète (« comme user »). */
    private final User user;

    /**
     * Authorities ({@code VERB:RESOURCE}) octroyées par les memberships actives (P1), additionnées
     * aux authorities du rôle de base dans {@link #getAuthorities()}. Sérialisé <b>par champ</b>
     * dans le cache Redis (FIELD visibility — cf {@code UserDetailsCacheConfig}) ; vide pour un user
     * sans membership programme.
     */
    private final Set<String> programAuthorities;

    @JsonCreator
    public OneClickUserDetails(@JsonProperty("user") User user,
                               @JsonProperty("programAuthorities") Set<String> programAuthorities) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (user.getRole() == null) {
            throw new IllegalStateException(
                "User " + user.getId() + " has no role — RBAC requires 1 role per user");
        }
        this.user = user;
        this.programAuthorities = programAuthorities != null ? Set.copyOf(programAuthorities) : Set.of();
    }

    /** Fabrique sans authorities de membership (rétro-compat — équivaut à un user sans programme). */
    public static OneClickUserDetails from(User user) {
        return new OneClickUserDetails(user, Set.of());
    }

    /** Fabrique avec les authorities de membership pliées (P1 — {@code MembershipDirectoryApi}). */
    public static OneClickUserDetails from(User user, Set<String> programAuthorities) {
        return new OneClickUserDetails(user, programAuthorities);
    }

    // ─── UserDetails ─────────────────────────────────────────────────────────
    // Getters dérivés @JsonIgnore : seul le champ {@code user} est sérialisé.

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        var role = user.getRole();
        String code = role.getCode();
        List<GrantedAuthority> auths = new ArrayList<>();
        // Backward-compat : rôle exposé en double pour hasRole(...) + hasAuthority(<role>)
        auths.add(new SimpleGrantedAuthority("ROLE_" + code));
        auths.add(new SimpleGrantedAuthority(code));
        // RBAC v2 : permissions du rôle au format VERB:RESOURCE
        for (Permission p : role.getPermissions()) {
            if (p.getAction() != null && p.getMenu() != null) {
                auths.add(new SimpleGrantedAuthority(p.getAction().getCode() + ":" + p.getMenu().getCode()));
            }
        }
        // P1 — authorities octroyées par les memberships actives (modèle « l'invitation accorde les
        // permissions ») : un client n'accède au contenu d'un programme que via sa membership.
        if (programAuthorities != null) {
            for (String pa : programAuthorities) {
                auths.add(new SimpleGrantedAuthority(pa));
            }
        }
        return auths;
    }

    /** Mot de passe caché : jamais consommé (login = {@code AuthService} sur l'entité). */
    @Override @JsonIgnore public String getPassword() { return null; }

    /** Convention OneClick : username = UUID string, matche {@code jwt.sub}. */
    @Override @JsonIgnore public String getUsername() { return user.getId().toString(); }

    @Override @JsonIgnore public boolean isAccountNonExpired() { return user.isAccountNonExpired(); }
    @Override @JsonIgnore public boolean isAccountNonLocked() { return user.isAccountNonLocked(); }
    @Override @JsonIgnore public boolean isCredentialsNonExpired() { return user.isCredentialsNonExpired(); }
    @Override @JsonIgnore public boolean isEnabled() { return user.isEnabled(); }
}
