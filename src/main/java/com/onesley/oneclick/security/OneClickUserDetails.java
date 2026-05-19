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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Bug 34 — {@link UserDetails} OneClick : enveloppe immuable autour de {@link User}
 * pour Spring Security.
 *
 * <p>Construit une fois par auth via {@code OneClickUserDetailsService} ; ensuite
 * mis en cache Redis (cache {@code userDetails}, TTL 1 h) pour éviter les SELECT
 * répétés sur chaque requête authentifiée. La sérialisation Jackson exige des
 * primitives uniquement — on extrait donc les champs scalaires (pas la référence
 * {@code User} qui contient des proxies LAZY {@code tenant/role}).
 *
 * <p>Authorities produites au {@link #from(User)} (cumul, pas exclusif) :
 * <ul>
 *   <li>{@code ROLE_<code>} — backward-compat {@code hasRole(...)}</li>
 *   <li>{@code <code>}     — backward-compat {@code hasAuthority(<role>)}</li>
 *   <li>{@code VERB:RESOURCE} (ex: {@code VIEW:RESTAURANTS}) — RBAC v2 senior
 *       {@code hasAuthority('VERB:RESOURCE')} (Bug 32)</li>
 * </ul>
 *
 * <p>{@code username} = UUID du user (string) — matche le {@code sub} claim du
 * JWT pour un lookup direct {@code loadUserByUsername(jwt.sub)} sans table de
 * correspondance email → uuid.
 */
public final class OneClickUserDetails implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String passwordHash;
    private final boolean accountNonExpired;
    private final boolean accountNonLocked;
    private final boolean credentialsNonExpired;
    private final boolean enabled;
    /**
     * Authorities stockées en {@link String} (et pas en {@link SimpleGrantedAuthority})
     * pour rester serializable sans dépendance fragile sur le type Spring Security
     * en JSON (Jackson default typing ajoute déjà {@code @class} sur la List).
     */
    private final List<String> authorityStrings;

    /**
     * Constructeur principal — extraction à partir d'un {@link User} chargé avec
     * son {@code role} + {@code role.permissions} (cf
     * {@code UserRepository.findByIdWithRoleAndPermissions}). Les LAZY proxies
     * doivent être résolus AVANT que la transaction Hibernate ne ferme.
     */
    public static OneClickUserDetails from(User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (user.getRole() == null) {
            throw new IllegalStateException(
                "User " + user.getId() + " has no role — RBAC requires 1 role per user");
        }
        return new OneClickUserDetails(
            user.getId(),
            user.getPasswordHash(),
            user.isAccountNonExpired(),
            user.isAccountNonLocked(),
            user.isCredentialsNonExpired(),
            user.isEnabled(),
            buildAuthorityStrings(user)
        );
    }

    /**
     * Constructeur explicite — utilisé par Jackson lors du hit cache Redis pour
     * reconstruire l'objet depuis JSON.
     */
    @JsonCreator
    public OneClickUserDetails(
        @JsonProperty("userId") UUID userId,
        @JsonProperty("passwordHash") String passwordHash,
        @JsonProperty("accountNonExpired") boolean accountNonExpired,
        @JsonProperty("accountNonLocked") boolean accountNonLocked,
        @JsonProperty("credentialsNonExpired") boolean credentialsNonExpired,
        @JsonProperty("enabled") boolean enabled,
        @JsonProperty("authorityStrings") List<String> authorityStrings
    ) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.accountNonExpired = accountNonExpired;
        this.accountNonLocked = accountNonLocked;
        this.credentialsNonExpired = credentialsNonExpired;
        this.enabled = enabled;
        this.authorityStrings = authorityStrings == null
            ? Collections.emptyList()
            : List.copyOf(authorityStrings);
    }

    /**
     * Construction des authorities strings depuis {@code Role} + {@code Role.permissions}
     * (chargés en JOIN FETCH). Format aligné sur {@code UserRoleAuthoritiesConverter}
     * historique (Bug 32 RBAC v2 senior strict).
     */
    private static List<String> buildAuthorityStrings(User user) {
        var role = user.getRole();
        String code = role.getCode();
        List<String> out = new ArrayList<>();
        // Backward-compat : rôle exposé en double pour hasRole + hasAuthority(role)
        out.add("ROLE_" + code);
        out.add(code);
        // RBAC v2 — Bug 32 : permissions du rôle au format VERB:RESOURCE
        for (Permission p : role.getPermissions()) {
            if (p.getAction() != null && p.getMenu() != null) {
                out.add(p.getAction().getCode() + ":" + p.getMenu().getCode());
            }
        }
        return List.copyOf(out);
    }

    // ─── UserDetails ─────────────────────────────────────────────────────────
    // {@code @JsonIgnore} sur les getters dérivés : Jackson sérialise déjà les
    // champs primitifs ci-dessus (cf @JsonCreator), donc inutile + dangereux
    // de re-publier {@code authorities}, {@code username}, {@code password}
    // comme propriétés JSON sans setter ni argument constructeur (Spring Data
    // Redis lance {@code SerializationException} "setterless property" sinon).

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Conversion à la lecture — pas stockée pour rester JSON-friendly
        return authorityStrings.stream()
            .map(s -> (GrantedAuthority) new SimpleGrantedAuthority(s))
            .toList();
    }

    @Override @JsonIgnore public String getPassword() { return passwordHash; }
    /** Convention OneClick : username = UUID string, matche {@code jwt.sub}. */
    @Override @JsonIgnore public String getUsername() { return userId.toString(); }
    @Override public boolean isAccountNonExpired() { return accountNonExpired; }
    @Override public boolean isAccountNonLocked() { return accountNonLocked; }
    @Override public boolean isCredentialsNonExpired() { return credentialsNonExpired; }
    @Override public boolean isEnabled() { return enabled; }

    // ─── Accesseurs métier (utilisés par tests + cache eviction) ─────────────
    public UUID getUserId() { return userId; }
    public List<String> getAuthorityStrings() { return authorityStrings; }
}
