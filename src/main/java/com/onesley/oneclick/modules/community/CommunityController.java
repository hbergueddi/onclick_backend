package com.onesley.oneclick.modules.community;

import com.onesley.oneclick.shared.PageResponse;
import com.onesley.oneclick.modules.community.internal.CommunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.community.api.CommunityDtos.*;

/**
 * Bug 32 (Batch D RBAC v2) — RBAC v2 senior strict hasAuthority('VERB:COMMUNITY')
 */
@RestController
@RequestMapping("/api/community")
@Tag(name = "Community", description = "Posts + commentaires + likes (§8)")
public class CommunityController {

    private final CommunityService service;

    public CommunityController(CommunityService service) {
        this.service = service;
    }

    // ─── Posts ───────────────────────────────────────────────────────────────

    @GetMapping("/posts")
    @Operation(summary = "Feed paginé — filtre authorId optionnel")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public PageResponse<PostDto> findAllPosts(
        @RequestParam(required = false) UUID authorId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return PageResponse.from(service.findAllPosts(authorId, page, size));
    }

    @GetMapping("/posts/{id}")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public PostDto findPostById(@PathVariable UUID id) { return service.findPostById(id); }

    @PostMapping("/posts")
    @PreAuthorize("hasAuthority('CREATE:COMMUNITY')")
    public ResponseEntity<PostDto> createPost(@Valid @RequestBody PostCreateDto dto) {
        PostDto p = service.createPost(dto);
        return ResponseEntity.created(URI.create("/api/community/posts/" + p.id())).body(p);
    }

    @DeleteMapping("/posts/{id}")
    @PreAuthorize("hasAuthority('DELETE:COMMUNITY')")
    public ResponseEntity<Void> deletePost(@PathVariable UUID id) {
        service.softDeletePost(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // ─── Comments ────────────────────────────────────────────────────────────

    @GetMapping("/posts/{postId}/comments")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<CommentDto> findCommentsByPost(@PathVariable UUID postId) {
        return service.findCommentsByPost(postId);
    }

    @PostMapping("/comments")
    @PreAuthorize("hasAuthority('CREATE:COMMUNITY')")
    public ResponseEntity<CommentDto> createComment(@Valid @RequestBody CommentCreateDto dto) {
        CommentDto c = service.createComment(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(c);
    }

    // ─── Likes ───────────────────────────────────────────────────────────────

    @GetMapping("/posts/{postId}/likes")
    @PreAuthorize("hasAuthority('VIEW:COMMUNITY')")
    public List<PostLikeDto> findLikesByPost(@PathVariable UUID postId) {
        return service.findLikesByPost(postId);
    }

    @PostMapping("/likes")
    @PreAuthorize("hasAuthority('CREATE:COMMUNITY')")
    public ResponseEntity<PostLikeDto> like(@Valid @RequestBody PostLikeCreateDto dto) {
        PostLikeDto l = service.like(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(l);
    }
}
