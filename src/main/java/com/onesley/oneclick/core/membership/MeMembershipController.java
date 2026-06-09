package com.onesley.oneclick.core.membership;

import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi.MembershipView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Self-service des appartenances (memberships) du client courant — P3 « révélation ».
 *
 * <p>Le front <b>Win</b> lit {@code GET /api/me/memberships} pour révéler les espaces programme
 * (PCC/HOMU/...) auxquels le membre a accès et activer le <b>thème contextuel</b> du tenant. Le
 * reveal se fait par <b>membership</b> (et non plus par {@code users.tenant_id}, qui devient
 * {@code oneclick} pour tous après le flip P3).</p>
 *
 * <p><b>Architecture</b> : endpoint porté par le module {@code membership} (qui owne le concept),
 * et NON ajouté à {@code /me/context} du module {@code identity} — sinon {@code identity → membership}
 * créerait un cycle Modulith ({@code membership → identity.api} existe déjà). Self-scoped : on ne
 * lit QUE ses propres memberships (sub du JWT). Sécurité : {@code hasAuthority('VIEW:PROFILE')}
 * (autorité self-profil détenue par tout compte, y compris CLIENT).</p>
 */
@RestController
@RequestMapping("/api/me")
@Tag(name = "Memberships", description = "Membres d'un programme (invitation par l'admin du tenant)")
@RequiredArgsConstructor
public class MeMembershipController {

    private final MembershipDirectoryApi membershipDirectory;

    @GetMapping("/memberships")
    @Operation(summary = "Mes appartenances actives aux programmes (reveal des espaces — Win)")
    @PreAuthorize("hasAuthority('VIEW:PROFILE')")
    public List<MembershipView> myMemberships() {
        UUID me = currentUserId();
        return me == null ? List.of() : membershipDirectory.activeMembershipViews(me);
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            try {
                return UUID.fromString(jwt.getToken().getSubject());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
