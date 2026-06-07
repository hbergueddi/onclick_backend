package com.onesley.oneclick.core.identity;

import com.onesley.oneclick.search.SearchRequest;
import com.onesley.oneclick.core.identity.internal.UserService;
import com.onesley.oneclick.search.Searchable;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Set;
import java.util.UUID;
import com.onesley.oneclick.core.identity.api.PasswordChangeDto;
import com.onesley.oneclick.core.identity.api.UserCreateDto;
import com.onesley.oneclick.core.identity.api.UserRegisterDto;
import com.onesley.oneclick.core.identity.api.UserDto;
import com.onesley.oneclick.core.identity.api.UserUpdateDto;
import com.onesley.oneclick.core.identity.api.PccMemberTypeUpdateDto;
import com.onesley.oneclick.core.identity.api.MeContextDto;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;

/**
 * REST controller {@code /api/users}.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Identités applicatives (RBAC simplifié 1 user = 1 role)")
@RequiredArgsConstructor
public class UserController {

    /** Whitelist Phase 4 spec §6.3 — champs filtrables/sortables via /search. */
    private static final Set<String> SEARCHABLE_FIELDS = Set.of(
        "email", "phone", "firstName", "lastName", "language", "status",
        "tenantId", "roleId", "lastLoginAt", "createdAt", "updatedAt", "enabled"
    );

    private final UserService service;
    private final UserRepository userRepository;

    // Bug 32 (Batch B RBAC v2) — RBAC v2 senior strict hasAuthority('VERB:USERS')
    @GetMapping
    @Operation(summary = "Liste paginée des users")
    @PreAuthorize("hasAuthority('VIEW:USERS')")
    public PageResponse<UserDto> findAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAll(page, size));
    }

    @GetMapping("/me")
    @Operation(
        summary = "User courant (depuis JWT.sub) — évite au frontend de parser le JWT",
        description = "Retourne le UserDto du user actuellement authentifié. 401 si pas de JWT."
    )
    @PreAuthorize("hasAuthority('VIEW:PROFILE')")
    public UserDto findMe() {
        return service.findMe();
    }

    @GetMapping("/me/permissions")
    @Operation(
        summary = "Bug 32 (RBAC v2) — Authorities du user courant au format VERB:RESOURCE.",
        description = "Permet au frontend de cacher les CTA pour lesquels l'utilisateur n'a " +
                      "pas la permission (UX cosmétique — le backend reste la source de vérité " +
                      "via @PreAuthorize). Cache TanStack frontend conseillé : staleTime 5 min."
    )
    @PreAuthorize("hasAuthority('VIEW:PROFILE')")
    public java.util.List<String> findMyPermissions(
        org.springframework.security.core.Authentication authentication
    ) {
        if (authentication == null) return java.util.List.of();
        return authentication.getAuthorities().stream()
            .map(org.springframework.security.core.GrantedAuthority::getAuthority)
            // Ne renvoie QUE les authorities format VERB:RESOURCE (un ":" séparateur)
            // — exclut ROLE_SUPERADMIN, SUPERADMIN, etc. qui sont là pour
            // backward-compat hasRole/hasAuthority(<role>) mais ne sont pas du RBAC v2.
            .filter(a -> a != null && a.contains(":"))
            .sorted()
            .toList();
    }

    @GetMapping("/me/context")
    @Operation(
        summary = "Contexte complet du user courant — profil + rôle + menus + permissions (1 appel).",
        description = "Amorçage front consolidé : remplace les multiples appels (/me + /me/permissions) " +
                      "et expose les menus (sidebar) accessibles selon le rôle. Construit côté domaine " +
                      "identity, indépendamment de l'adaptateur Spring Security."
    )
    @PreAuthorize("hasAuthority('VIEW:PROFILE')")
    public MeContextDto meContext() {
        return service.findMeContext();
    }

    @PatchMapping("/me")
    @Operation(
        summary = "Mise à jour de SON PROPRE profil (self-service) — UPDATE:PROFILE, sans droit admin",
        description = "Self-service Pocket : un user édite ses champs sûrs (firstName/lastName/phone/" +
                      "avatarUrl/language via UserUpdateDto — PAS de role/tenant/status). Gardé par " +
                      "UPDATE:PROFILE (≠ UPDATE:USERS admin). Self par construction (JWT.sub)."
    )
    @PreAuthorize("hasAuthority('UPDATE:PROFILE')")
    public UserDto patchMe(@Valid @RequestBody UserUpdateDto dto) {
        UUID me = SecurityHelper.currentUserId();
        if (me == null) throw new ForbiddenException("Authentification requise");
        return service.patch(me, dto);
    }

    @PostMapping("/me/password")
    @Operation(
        summary = "Change SON PROPRE mot de passe (self-service) — vérifie le mot de passe courant",
        description = "Self-service : gardé par UPDATE:PROFILE (≠ UPDATE:USERS admin). Self par " +
                      "construction (JWT.sub). Vérifie currentPassword (BCrypt) avant de ré-encoder."
    )
    @PreAuthorize("hasAuthority('UPDATE:PROFILE')")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody PasswordChangeDto dto) {
        UUID me = SecurityHelper.currentUserId();
        if (me == null) throw new ForbiddenException("Authentification requise");
        service.changePassword(me, dto.currentPassword(), dto.newPassword());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un user par UUID — owner ou SUPERADMIN")
    @PreAuthorize("hasAuthority('VIEW:USERS')")
    public UserDto findById(@PathVariable UUID id) {
        SecurityHelper.requireOwnerOrAdmin(id);
        return service.findById(id);
    }

    @GetMapping("/by-email")
    @Operation(summary = "Lookup user par email — login flow + flow Inscrire membre (staff resto)")
    // Aligné sur findByPhone (isAuthenticated). Justif privacy : returns 404
    // ou UserDto basique — un staff qui veut enrôler un client par email a
    // un besoin légitime. Pas d'enum protection nécessaire au-delà de l'auth.
    @PreAuthorize("hasAuthority('VIEW:USERS')")
    public UserDto findByEmail(@RequestParam String email) {
        return service.findByEmail(email);
    }

    @GetMapping("/by-phone")
    @Operation(summary = "Lookup user par téléphone — flow invitation ami (Pocket)")
    @PreAuthorize("hasAuthority('VIEW:USERS')")
    public UserDto findByPhone(@RequestParam String phone) {
        return service.findByPhone(phone);
    }

    @GetMapping("/by-referral-code")
    @Operation(summary = "Lookup user par code parrain (OC-XXXXXX) — résolution code ami Pocket")
    @PreAuthorize("hasAuthority('VIEW:USERS')")
    public UserDto findByReferralCode(@RequestParam("code") String referralCode) {
        return service.findByReferralCode(referralCode);
    }

    @PostMapping("/by-ids")
    @Operation(
        summary = "Batch lookup par liste d'UUIDs — anti N+1",
        description = "Remplace POST /search avec op:IN pour les hooks d'enrichissement "
                    + "(useFriendships, useTeamMembers, useSupportTickets). Exposé en "
                    + "isAuthenticated() — l'appelant doit déjà connaître les UUIDs."
    )
    @PreAuthorize("hasAuthority('VIEW:USERS')")
    public java.util.List<UserDto> findByIds(@RequestBody java.util.List<java.util.UUID> ids) {
        return service.findAllByIds(ids);
    }

    @GetMapping("/by-role")
    @Operation(
        summary = "Liste paginée des users d'un rôle — SUPERADMIN ou STAFF (picker Login)",
        description = "Filtre par code de rôle (ex: CLIENT, STAFF) et tenant optionnel. Soft-deletes exclus."
    )
    @PreAuthorize("hasAuthority('VIEW:USERS')")
    public PageResponse<UserDto> findByRole(
        @RequestParam String role,
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findByRole(role, tenantId, page, size));
    }

    @GetMapping("/roles-distribution")
    @Operation(summary = "Nombre d'utilisateurs par rôle — dashboard admin GestionRoles")
    @PreAuthorize("hasAuthority('VIEW:USERS')")
    public java.util.List<com.onesley.oneclick.core.identity.api.RoleDistributionDto> rolesDistribution() {
        return service.rolesDistribution();
    }

    @GetMapping("/clients/search")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @Operation(
        summary = "Recherche clients — OR firstName / lastName / phone, en une requête.",
        description = "Remplace les 3 ILIKE fusionnés côté client de DistributionPanel (admin Wallet). " +
                      "Scopé rôle CLIENT (cible des distributions de points). q < 2 caractères → liste vide " +
                      "(évite les gros scans). @Transactional pour l'accès lazy à User.role dans toDto()."
    )
    @PreAuthorize("hasAuthority('VIEW:USERS')")
    public java.util.List<UserDto> searchClients(
        @RequestParam String q,
        @RequestParam(defaultValue = "30") int limit
    ) {
        if (q == null || q.trim().length() < 2) return java.util.List.of();
        int capped = Math.min(Math.max(limit, 1), 100);
        return userRepository.searchClients(
                q.trim(), org.springframework.data.domain.PageRequest.of(0, capped))
            .stream().map(User::toDto).toList();
    }

    @PostMapping
    @Operation(summary = "Crée un user avec rôle arbitraire (création admin — authentifié). "
        + "Pour le signup public, utiliser POST /api/users/register.")
    public ResponseEntity<UserDto> create(@Valid @RequestBody UserCreateDto dto) {
        UserDto created = service.create(dto);
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }

    @PostMapping("/register")
    @Operation(summary = "Inscription PUBLIQUE (permitAll) — crée un compte CLIENT. "
        + "Le rôle est forcé serveur-side (anti escalade de privilèges) ; pas de roleId dans le body.")
    public ResponseEntity<UserDto> register(@Valid @RequestBody UserRegisterDto dto) {
        UserDto created = service.register(dto);
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Mise à jour partielle d'un user — owner ou SUPERADMIN")
    @PreAuthorize("hasAuthority('UPDATE:USERS')")
    public UserDto patch(@PathVariable UUID id, @Valid @RequestBody UserUpdateDto dto) {
        SecurityHelper.requireOwnerOrAdmin(id);
        return service.patch(id, dto);
    }

    @PatchMapping("/{id}/pcc-member-type")
    @Operation(
        summary = "H — définit le type de membre PCC (resident|non_resident|null) — admin",
        description = "Pilote la remise PCC (résidant -20% / non-résidant -15%). Action ADMIN " +
                      "(UPDATE:USERS) : PAS owner-scope (un membre ne s'auto-attribue pas une remise). " +
                      "memberType null retire le statut."
    )
    @PreAuthorize("hasAuthority('UPDATE:USERS')")
    public UserDto updatePccMemberType(
        @PathVariable UUID id,
        @Valid @RequestBody PccMemberTypeUpdateDto dto
    ) {
        return service.updatePccMemberType(id, dto.memberType());
    }

    @PostMapping("/{id}/password")
    @Operation(
        summary = "Change le mot de passe — owner exact uniquement (admins refusés)",
        description = "Vérifie le mot de passe courant puis ré-encode le nouveau (BCrypt 12). " +
                      "Strict ownership : un admin ne peut PAS changer le password de quelqu'un d'autre."
    )
    @PreAuthorize("hasAuthority('UPDATE:USERS')")
    public ResponseEntity<Void> changePassword(
        @PathVariable UUID id,
        @Valid @RequestBody PasswordChangeDto dto
    ) {
        SecurityHelper.requireOwnerExact(id);
        service.changePassword(id, dto.currentPassword(), dto.newPassword());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete d'un user — owner ou SUPERADMIN")
    @PreAuthorize("hasAuthority('DELETE:USERS')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        SecurityHelper.requireOwnerOrAdmin(id);
        service.softDelete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/search")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('VIEW:USERS')")
    @Operation(
        summary = "Recherche dynamique (Phase 4 §6.3) — 12 opérateurs + whitelist",
        description = "Body : SearchRequest. Champs autorisés : email, phone, firstName, lastName, language, status, tenantId, roleId, lastLoginAt, createdAt, updatedAt, enabled."
    )
    public PageResponse<UserDto> search(@RequestBody SearchRequest req) {
        // @Transactional ouvre une session Hibernate qui couvre l'accès lazy à User.role
        // → évite LazyInitializationException quand User.toDto() appelle user.getRole().getCode()
        return PageResponse.from(
            Searchable.execute(userRepository, req, SEARCHABLE_FIELDS, User::toDto)
        );
    }
}
