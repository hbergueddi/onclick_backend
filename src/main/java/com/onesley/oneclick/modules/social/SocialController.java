package com.onesley.oneclick.modules.social;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.onesley.oneclick.modules.social.internal.SocialService;
import com.onesley.oneclick.security.SecurityHelper;

import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.social.api.SocialDtos.*;

@RestController
@RequestMapping("/api/social")
@Tag(name = "Social", description = "Amitiés + parrainages (§8)")
public class SocialController {

    private final SocialService service;

    public SocialController(SocialService service) {
        this.service = service;
    }

    public record ActivateReferralDto(UUID referredUserId) {}

    // ─── Friendships ─────────────────────────────────────────────────────────

    @GetMapping("/friendships/by-user/{userId}")
    @Operation(summary = "Liste des amis acceptés d'un user")
    @PreAuthorize("isAuthenticated()")
    public List<FriendshipDto> findFriendsOf(@PathVariable UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return service.findFriendsOf(userId);
    }

    @PostMapping("/friendships")
    @Operation(summary = "Demande d'amitié (pending)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FriendshipDto> request(@Valid @RequestBody FriendshipCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.request(dto));
    }

    @PatchMapping("/friendships/{id}/accept")
    @Operation(summary = "Accepte une demande d'amitié")
    @PreAuthorize("isAuthenticated()")
    public FriendshipDto accept(@PathVariable UUID id) { return service.accept(id); }

    @PatchMapping("/friendships/{id}/decline")
    @Operation(summary = "Refuse une demande d'amitié")
    @PreAuthorize("isAuthenticated()")
    public FriendshipDto decline(@PathVariable UUID id) { return service.decline(id); }

    // ─── Referrals ───────────────────────────────────────────────────────────

    @GetMapping("/referrals/by-referrer/{referrerId}")
    @PreAuthorize("isAuthenticated()")
    public List<ReferralDto> findByReferrer(@PathVariable UUID referrerId) {
        SecurityHelper.requireOwnerOrAdmin(referrerId);
        return service.findByReferrer(referrerId);
    }

    @PostMapping("/referrals")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReferralDto> create(@Valid @RequestBody ReferralCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PatchMapping("/referrals/{id}/activate")
    @Operation(summary = "Active un parrainage (lors du signup du parrainé)")
    @PreAuthorize("isAuthenticated()")
    public ReferralDto activate(@PathVariable UUID id, @RequestBody ActivateReferralDto body) {
        return service.activate(id, body.referredUserId());
    }
}
