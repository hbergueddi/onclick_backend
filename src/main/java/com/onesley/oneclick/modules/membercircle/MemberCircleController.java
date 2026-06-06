package com.onesley.oneclick.modules.membercircle;

import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostsResultDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.RejectMemberPostDto;
import com.onesley.oneclick.modules.membercircle.internal.MemberPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * « Circle » — modération des posts membres (C4.8c), portail tenant-admin SUPERADMIN-only.
 *
 * <p>RBAC v2 strict : {@code @PreAuthorize("hasAuthority('VERB:TENANTS')")} (jamais hasRole/
 * isAuthenticated). Cohérent avec tout le portail tenant-admin (C4) — SUPERADMIN possède
 * VIEW/UPDATE/DELETE:TENANTS (V32). Pas de nouvelle ressource RBAC.
 */
@RestController
@RequestMapping("/api/member-posts")
@Tag(name = "MemberCircle", description = "Modération des posts membres (Circle) — tenant-admin (C4.8c)")
@RequiredArgsConstructor
public class MemberCircleController {

    private final MemberPostService service;

    @GetMapping
    @Operation(summary = "Posts membres d'un tenant + résumé (modération) — filtre statut optionnel")
    @PreAuthorize("hasAuthority('VIEW:TENANTS')")
    public MemberPostsResultDto list(
        @RequestParam UUID tenantId,
        @RequestParam(required = false) String status
    ) {
        return service.list(tenantId, status);
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Approuve un post membre")
    @PreAuthorize("hasAuthority('UPDATE:TENANTS')")
    public ResponseEntity<MemberPostDto> approve(@PathVariable UUID id) {
        MemberPostDto dto = service.approve(id);
        return dto == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(dto);
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Rejette un post membre (motif optionnel)")
    @PreAuthorize("hasAuthority('UPDATE:TENANTS')")
    public ResponseEntity<MemberPostDto> reject(
        @PathVariable UUID id, @Valid @RequestBody(required = false) RejectMemberPostDto body
    ) {
        MemberPostDto dto = service.reject(id, body == null ? null : body.reason());
        return dto == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprime (soft) un post membre")
    @PreAuthorize("hasAuthority('DELETE:TENANTS')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return service.delete(id)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }
}
