package com.onesley.oneclick.controller.auth;

import com.onesley.oneclick.dto.auth.UserRoleCreateDto;
import com.onesley.oneclick.dto.auth.UserRoleDto;
import com.onesley.oneclick.entity.shared.AppRole;
import com.onesley.oneclick.service.auth.UserRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller {@code /api/user-roles} — pilote du pattern controller OneClick.
 *
 * <p>Conventions :
 * <ul>
 *   <li>Préfixe {@code /api/...} (réservé aux endpoints REST applicatifs)</li>
 *   <li>OpenAPI/Swagger annoté via {@code @Tag} + {@code @Operation}</li>
 *   <li>Validation des payloads via {@code @Valid} + Bean Validation</li>
 *   <li>POST → 201 Created + Location header pointant vers la ressource créée</li>
 *   <li>DELETE → 204 No Content</li>
 *   <li>Aucune logique métier ici — uniquement orchestration HTTP ↔ service</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/user-roles")
@Tag(name = "User Roles", description = "Attribution et révocation des rôles applicatifs")
@PreAuthorize("hasRole('admin')")
public class UserRoleController {

    private final UserRoleService service;

    public UserRoleController(UserRoleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste les rôles d'un utilisateur (param userId obligatoire)")
    public List<UserRoleDto> findByUser(@RequestParam UUID userId) {
        return service.findByUser(userId);
    }

    @GetMapping("/check")
    @Operation(summary = "Vérifie qu'un utilisateur porte un rôle donné")
    public boolean userHasRole(@RequestParam UUID userId, @RequestParam AppRole role) {
        return service.userHasRole(userId, role);
    }

    @PostMapping
    @Operation(summary = "Attribue un rôle à un utilisateur")
    public ResponseEntity<UserRoleDto> assign(@Valid @RequestBody UserRoleCreateDto dto) {
        UserRoleDto created = service.assign(dto);
        URI location = URI.create("/api/user-roles/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Révoque un rôle attribué")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        service.revoke(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
