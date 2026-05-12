package com.onesley.oneclick.modules.community.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;
import com.onesley.oneclick.modules.community.internal.Comment;
import com.onesley.oneclick.modules.community.internal.Post;
import com.onesley.oneclick.modules.community.internal.PostLike;

/**
 * DTOs du module community — Post / Comment / PostLike.
 * Regroupés ici pour limiter le nombre de fichiers (entités simples).
 */
public final class CommunityDtos {

    private CommunityDtos() {}

    public record PostDto(UUID id, UUID authorId, String content, String visibility, Instant createdAt) {
        public static PostDto from(Post p) {
            return new PostDto(p.getId(), p.getAuthorId(), p.getContent(), p.getVisibility(), p.getCreatedAt());
        }
    }

    public record PostCreateDto(
        @NotNull UUID authorId,
        @NotBlank String content,
        @Pattern(regexp = "^(public|friends|private)$") String visibility
    ) {}

    public record CommentDto(UUID id, UUID postId, UUID authorId, String content, Instant createdAt) {
        public static CommentDto from(Comment c) {
            return new CommentDto(c.getId(), c.getPostId(), c.getAuthorId(), c.getContent(), c.getCreatedAt());
        }
    }

    public record CommentCreateDto(@NotNull UUID postId, @NotNull UUID authorId, @NotBlank String content) {}

    public record PostLikeDto(UUID id, UUID postId, UUID userId, Instant createdAt) {
        public static PostLikeDto from(PostLike l) {
            return new PostLikeDto(l.getId(), l.getPostId(), l.getUserId(), l.getCreatedAt());
        }
    }

    public record PostLikeCreateDto(@NotNull UUID postId, @NotNull UUID userId) {}
}
