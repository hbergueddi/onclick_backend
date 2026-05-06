package com.onesley.oneclick.controller.reservation;

import com.onesley.oneclick.dto.reservation.FriendGroupMemberDto;
import com.onesley.oneclick.service.reservation.FriendGroupMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller pour {@link FriendGroupMemberDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination, mutations).
 *
 * <p>Sécurité par défaut : hasAnyRole('admin','restaurateur','client').
 * À raffiner endpoint par endpoint quand la business logic est portée (Phase 11+).
 */
@RestController
@RequestMapping("/api/friend-group-members")
@Tag(name = "FriendGroupMember", description = "Auto-generated controller for friend_group_members")
@PreAuthorize("hasAnyRole('admin','restaurateur','client')")
public class FriendGroupMemberController {

    private final FriendGroupMemberService service;

    public FriendGroupMemberController(FriendGroupMemberService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<FriendGroupMemberDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une FriendGroupMember par UUID")
    public ResponseEntity<FriendGroupMemberDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
