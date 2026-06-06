package com.onesley.oneclick.modules.membercircle.api;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs « Circle » — modération des posts membres (C4.8c, tranche modération).
 *
 * <p>Vue tenant-admin (SUPERADMIN) : lister les posts d'un tenant par statut + approuver / rejeter
 * (avec motif) / supprimer. Le flux de création/feed membre (likes, commentaires, mentions) est un
 * lot futur séparé — ce module ne porte que le socle + la modération.
 */
public final class MemberPostDtos {

    private MemberPostDtos() {}

    /** Post membre enrichi (auteur) — vue modération. */
    public record MemberPostDto(
        UUID id,
        UUID authorId,
        String authorFirstName,
        String authorLastName,
        String authorAvatarUrl,
        String content,
        String photoUrl,
        String activityTag,
        String status,
        String rejectionReason,
        Instant createdAt
    ) {}

    /** Résumé (compteurs par statut). */
    public record MemberPostsSummaryDto(
        long total,
        long pending,
        long approved,
        long rejected
    ) {}

    /** Résultat complet de la vue modération. */
    public record MemberPostsResultDto(
        List<MemberPostDto> posts,
        MemberPostsSummaryDto summary
    ) {}

    /** Corps du rejet (motif optionnel). */
    public record RejectMemberPostDto(
        @Size(max = 512) String reason
    ) {}
}
