package com.onesley.oneclick.modules.community.internal;

import com.onesley.oneclick.core.identity.internal.User;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.community.api.CommunityDtos.*;
import com.onesley.oneclick.modules.community.api.CommunityDtos;
import com.onesley.oneclick.modules.community.api.CommunityDtos.CommentCreateDto;
import com.onesley.oneclick.modules.community.api.CommunityDtos.CommentDto;
import com.onesley.oneclick.modules.community.api.CommunityDtos.PostCreateDto;
import com.onesley.oneclick.modules.community.api.CommunityDtos.PostDto;
import com.onesley.oneclick.modules.community.api.CommunityDtos.PostLikeCreateDto;
import com.onesley.oneclick.modules.community.api.CommunityDtos.PostLikeDto;

@Service
@Transactional(readOnly = true)
public class CommunityService {

    private final PostRepository postRepo;
    private final CommentRepository commentRepo;
    private final PostLikeRepository likeRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public CommunityService(PostRepository postRepo, CommentRepository commentRepo, PostLikeRepository likeRepo) {
        this.postRepo = postRepo;
        this.commentRepo = commentRepo;
        this.likeRepo = likeRepo;
    }

    // ─── Posts ───────────────────────────────────────────────────────────────

    public Page<PostDto> findAllPosts(UUID authorId, int page, int size) {
        Specification<Post> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (authorId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("authorId"), authorId));
        return postRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(Post::toDto);
    }

    public PostDto findPostById(UUID id) {
        return postRepo.findById(id)
            .filter(p -> p.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Post", id))
            .toDto();
    }

    @Transactional
    public PostDto createPost(PostCreateDto dto) {
        User authorRef = entityManager.getReference(User.class, dto.authorId());
        Post p = new Post(UUID.randomUUID(), authorRef, dto.content());
        if (dto.visibility() != null) p.setVisibility(dto.visibility());
        return postRepo.save(p).toDto();
    }

    @Transactional
    public void softDeletePost(UUID id) {
        Post p = postRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Post", id));
        SecurityHelper.requireOwnerOrAdmin(p.getAuthorId());
        p.markDeleted();
        postRepo.save(p);
    }

    // ─── Comments ────────────────────────────────────────────────────────────

    public List<CommentDto> findCommentsByPost(UUID postId) {
        return commentRepo.findAllByPostId(postId).stream()
            .filter(c -> c.getDeletedAt() == null)
            .map(Comment::toDto)
            .toList();
    }

    @Transactional
    public CommentDto createComment(CommentCreateDto dto) {
        Post postRef = entityManager.getReference(Post.class, dto.postId());
        User authorRef = entityManager.getReference(User.class, dto.authorId());
        Comment c = new Comment(UUID.randomUUID(), postRef, authorRef, dto.content());
        return commentRepo.save(c).toDto();
    }

    // ─── Likes ───────────────────────────────────────────────────────────────

    public List<PostLikeDto> findLikesByPost(UUID postId) {
        return likeRepo.findAllByPostId(postId).stream().map(PostLike::toDto).toList();
    }

    @Transactional
    public PostLikeDto like(PostLikeCreateDto dto) {
        Post postRef = entityManager.getReference(Post.class, dto.postId());
        User userRef = entityManager.getReference(User.class, dto.userId());
        PostLike l = new PostLike(UUID.randomUUID(), postRef, userRef);
        return likeRepo.save(l).toDto();
    }
}
