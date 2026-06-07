package com.onesley.oneclick.modules.membercircle.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    // ─── A.1 — flux membre (création / feed / like) ──────────────────────────

    /** Création membre d'un post (status=pending). tenant_id/author_id résolus serveur. */
    public record MemberPostCreateDto(
        @NotBlank @Size(max = 500) String content,
        @Size(max = 512) String photoUrl,
        @Pattern(regexp = "^(padel|tennis|foot|basket|spa|golf|coiffeur|palm_gym|restaurant|event|autre)$")
        String activityTag,
        /** A.2 — UUID (string) des membres mentionnés (optionnel). */
        List<String> mentionedUserIds
    ) {}

    /** Post du feed membre (approuvé) enrichi auteur + likes (likedByMe pour le viewer) + commentaires. */
    public record MemberPostFeedDto(
        UUID id,
        UUID authorId,
        String authorFirstName,
        String authorLastName,
        String authorAvatarUrl,
        String content,
        String photoUrl,
        String activityTag,
        long likesCount,
        boolean likedByMe,
        long commentsCount,
        List<String> mentionedUserIds,
        Instant createdAt
    ) {}

    /** Résultat d'un toggle like (état + compteur à jour). */
    public record LikeResultDto(boolean liked, long likesCount) {}

    // ─── A.2 — commentaires + mentions ───────────────────────────────────────

    /** Commentaire d'un post enrichi auteur (read-view). */
    public record MemberPostCommentDto(
        UUID id,
        UUID authorId,
        String authorFirstName,
        String authorLastName,
        String authorAvatarUrl,
        String content,
        List<String> mentionedUserIds,
        Instant createdAt
    ) {}

    /** Création d'un commentaire (≤ 500). */
    public record CommentCreateDto(
        @NotBlank @Size(max = 500) String content,
        List<String> mentionedUserIds
    ) {}

    /** Membre mentionnable (autocomplete @) — projection PII-light. */
    public record MentionableMemberDto(UUID id, String firstName, String lastName, String avatarUrl) {}
}
