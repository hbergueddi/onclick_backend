package com.onesley.oneclick.core.identity;

import com.onesley.oneclick.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller {@code /api/users}.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Identités applicatives (RBAC simplifié 1 user = 1 role)")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste paginée des users")
    public PageResponse<UserDto> findAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAll(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un user par UUID")
    public UserDto findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @GetMapping("/by-email")
    @Operation(summary = "Lookup user par email (login flow)")
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
    public UserDto patch(@PathVariable UUID id, @Valid @RequestBody UserUpdateDto dto) {
        return service.patch(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete d'un user")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.softDelete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
