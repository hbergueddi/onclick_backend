package com.onesley.oneclick.core.identity;

import com.onesley.oneclick.search.SearchRequest;
import com.onesley.oneclick.core.identity.internal.UserService;
import com.onesley.oneclick.search.Searchable;
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
import com.onesley.oneclick.core.identity.api.UserCreateDto;
import com.onesley.oneclick.core.identity.api.UserDto;
import com.onesley.oneclick.core.identity.api.UserUpdateDto;
import com.onesley.oneclick.core.identity.internal.UserRepository;

/**
 * REST controller {@code /api/users}.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Identités applicatives (RBAC simplifié 1 user = 1 role)")
public class UserController {

    /** Whitelist Phase 4 spec §6.3 — champs filtrables/sortables via /search. */
    private static final Set<String> SEARCHABLE_FIELDS = Set.of(
        "email", "phone", "firstName", "lastName", "language", "status",
        "tenantId", "roleId", "lastLoginAt", "createdAt", "updatedAt", "enabled"
    );

    private final UserService service;
    private final UserRepository userRepository;

    public UserController(UserService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Operation(summary = "Liste paginée des users")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public PageResponse<UserDto> findAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAll(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un user par UUID")
    @PreAuthorize("isAuthenticated()")
    // TODO RBAC : SecurityHelper.requireOwnerOrAdmin(...) à appeler en service
    public UserDto findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @GetMapping("/by-email")
    @Operation(summary = "Lookup user par email (login flow)")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public UserDto findByEmail(@RequestParam String email) {
        return service.findByEmail(email);
    }

    @PostMapping
    @Operation(summary = "Crée un user (signup ou création admin)")
    public ResponseEntity<UserDto> create(@Valid @RequestBody UserCreateDto dto) {
        UserDto created = service.create(dto);
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Mise à jour partielle d'un user")
    @PreAuthorize("isAuthenticated()")
    // TODO RBAC : SecurityHelper.requireOwnerOrAdmin(...) à appeler en service
    public UserDto patch(@PathVariable UUID id, @Valid @RequestBody UserUpdateDto dto) {
        return service.patch(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete d'un user")
    @PreAuthorize("isAuthenticated()")
    // TODO RBAC : SecurityHelper.requireOwnerOrAdmin(...) à appeler en service
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.softDelete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/search")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @PreAuthorize("hasRole('SUPERADMIN')")
    @Operation(
        summary = "Recherche dynamique (Phase 4 §6.3) — 12 opérateurs + whitelist",
        description = "Body : SearchRequest. Champs autorisés : email, phone, firstName, lastName, language, status, tenantId, roleId, lastLoginAt, createdAt, updatedAt, enabled."
    )
    public PageResponse<UserDto> search(@RequestBody SearchRequest req) {
        // @Transactional ouvre une session Hibernate qui couvre l'accès lazy à User.role
        // → évite LazyInitializationException quand UserDto.from() appelle user.getRole().getCode()
        return PageResponse.from(
            Searchable.execute(userRepository, req, SEARCHABLE_FIELDS, UserDto::from)
        );
    }
}
