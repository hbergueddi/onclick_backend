package com.onesley.oneclick.modules.membercircle;

import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.CommentCreateDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.LikeResultDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostCommentDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostCreateDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostFeedDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostsResultDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MentionableMemberDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.RejectMemberPostDto;
import com.onesley.oneclick.modules.membercircle.internal.MemberPostService;
import com.onesley.oneclick.security.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    // ─── A.1 — flux MEMBRE (mur communautaire) : RBAC COMMUNITY (le CLIENT possède
    //     VIEW + CREATE:COMMUNITY, V38). Self par construction (JWT.sub), pas d'ABAC tiers.

    @PostMapping
    @Operation(summary = "A — le membre publie un post (status=pending, modération a priori)")
    @PreAuthorize("hasAuthority('CREATE:COMMUNITY')")
    public ResponseEntity<MemberPostDto> createPost(@Valid @RequestBody MemberPostCreateDto dto) {
        MemberPostDto created = service.create(SecurityHelper.currentUserId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/feed")
    @Operation(summary = "A — feed du mur communautaire (posts approuvés du tenant du membre, likes inclus)")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<MemberPostFeedDto> feed(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "30") int size
    ) {
        return service.feed(SecurityHelper.currentUserId(), page, size);
    }

    @PostMapping("/{id}/like")
    @Operation(summary = "A — toggle like d'un post (idempotent) ; renvoie l'état + le compteur")
    @PreAuthorize("hasAuthority('CREATE:COMMUNITY')")
    public LikeResultDto toggleLike(@PathVariable UUID id) {
        return service.toggleLike(id, SecurityHelper.currentUserId());
    }

    @GetMapping("/mine/pending")
    @Operation(summary = "C9 — « Mes posts en attente » : mes posts non approuvés (pending/rejected) avec statut")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<MemberPostDto> myPending() {
        return service.myPosts(SecurityHelper.currentUserId());
    }

    @DeleteMapping("/mine/{id}")
    @Operation(summary = "C9 — le membre supprime SON propre post non approuvé (pending/rejected)")
    @PreAuthorize("hasAuthority('DELETE:COMMUNITY')")
    public ResponseEntity<Void> deleteOwnPost(@PathVariable UUID id) {
        service.deleteOwnPost(id, SecurityHelper.currentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "A.2 — commentaires d'un post approuvé (enrichis auteur)")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<MemberPostCommentDto> comments(@PathVariable UUID id) {
        return service.listComments(id);
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "A.2 — ajoute un commentaire (+ mentions) à un post approuvé")
    @PreAuthorize("hasAuthority('CREATE:COMMUNITY')")
    public ResponseEntity<MemberPostCommentDto> addComment(
        @PathVariable UUID id, @Valid @RequestBody CommentCreateDto dto
    ) {
        MemberPostCommentDto created = service.addComment(id, SecurityHelper.currentUserId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/mentionable")
    @Operation(summary = "A.2 — membres mentionnables du tenant (autocomplete @, PII-light)")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<MentionableMemberDto> mentionable(
        @RequestParam(required = false, defaultValue = "") String q,
        @RequestParam(defaultValue = "8") int limit
    ) {
        return service.mentionableMembers(SecurityHelper.currentUserId(), q, limit);
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    @Operation(summary = "A.2 — supprime un commentaire (auteur du commentaire OU auteur du post)")
    @PreAuthorize("hasAuthority('DELETE:COMMUNITY')")
    public ResponseEntity<Void> deleteComment(@PathVariable UUID id, @PathVariable UUID commentId) {
        service.deleteComment(id, commentId, SecurityHelper.currentUserId());
        return ResponseEntity.noContent().build();
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
