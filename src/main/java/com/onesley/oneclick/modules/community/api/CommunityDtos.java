package com.onesley.oneclick.modules.community.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs du module community — Post / Comment / PostLike.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Entity.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 */
public final class CommunityDtos {

    private CommunityDtos() {}

    public record PostDto(UUID id, UUID authorId, String content, String visibility, Instant createdAt) {}

    public record PostCreateDto(
        @NotNull UUID authorId,
        @NotBlank String content,
        @Pattern(regexp = "^(public|friends|private)$") String visibility
    ) {}

    public record CommentDto(UUID id, UUID postId, UUID authorId, String content, Instant createdAt) {}

    public record CommentCreateDto(@NotNull UUID postId, @NotNull UUID authorId, @NotBlank String content) {}

    public record PostLikeDto(UUID id, UUID postId, UUID userId, Instant createdAt) {}

    public record PostLikeCreateDto(@NotNull UUID postId, @NotNull UUID userId) {}
}
