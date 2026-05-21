package com.onesley.oneclick.modules.community.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.community.api.CommunityDtos.CommentCreateDto;
import com.onesley.oneclick.modules.community.api.CommunityDtos.PostCreateDto;
import com.onesley.oneclick.modules.community.api.CommunityDtos.PostLikeCreateDto;
import com.onesley.oneclick.security.SecurityHelper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class CommunityServiceTest {

    @Mock PostRepository postRepo;
    @Mock CommentRepository commentRepo;
    @Mock PostLikeRepository likeRepo;
    @Mock EntityManager em;
    @InjectMocks CommunityService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "entityManager", em);
        lenient().when(em.getReference(eq(User.class), any())).thenReturn(new User(UUID.randomUUID(), null, "u@x.ma", "h", "U", "U"));
        lenient().when(em.getReference(eq(Post.class), any())).thenReturn(new Post(UUID.randomUUID(), null, "c"));
        lenient().when(postRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(commentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(likeRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllPosts_delegates() {
        when(postRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAllPosts(UUID.randomUUID(), 0, 20).getContent()).isEmpty();
    }

    @Test
    void findPostById_notFoundAndFound() {
        when(postRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findPostById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        Post p = new Post(UUID.randomUUID(), null, "hello");
        when(postRepo.findById(p.getId())).thenReturn(Optional.of(p));
        assertThat(service.findPostById(p.getId())).isNotNull();
    }

    @Test
    void createPost_withAndWithoutVisibility() {
        assertThat(service.createPost(new PostCreateDto(UUID.randomUUID(), "hi", "public"))).isNotNull();
        assertThat(service.createPost(new PostCreateDto(UUID.randomUUID(), "hi", null))).isNotNull();
    }

    @Test
    void softDeletePost_notFoundAndSuccess() {
        when(postRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDeletePost(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        Post p = new Post(UUID.randomUUID(), null, "x");
        when(postRepo.findById(p.getId())).thenReturn(Optional.of(p));
        try (MockedStatic<SecurityHelper> ignored = mockStatic(SecurityHelper.class)) {
            service.softDeletePost(p.getId());
        }
        assertThat(p.getDeletedAt()).isNotNull();
    }

    @Test
    void findCommentsByPost_filtersDeleted() {
        Comment live = new Comment(UUID.randomUUID(), null, null, "ok");
        Comment deleted = new Comment(UUID.randomUUID(), null, null, "del");
        ReflectionTestUtils.setField(deleted, "deletedAt", Instant.now());
        when(commentRepo.findAllByPostId(any())).thenReturn(List.of(live, deleted));
        assertThat(service.findCommentsByPost(UUID.randomUUID())).hasSize(1);
    }

    @Test
    void createComment_andLike_andFindLikes() {
        assertThat(service.createComment(new CommentCreateDto(UUID.randomUUID(), UUID.randomUUID(), "comment"))).isNotNull();
        assertThat(service.like(new PostLikeCreateDto(UUID.randomUUID(), UUID.randomUUID()))).isNotNull();
        when(likeRepo.findAllByPostId(any())).thenReturn(List.of(new PostLike(UUID.randomUUID(), null, null)));
        assertThat(service.findLikesByPost(UUID.randomUUID())).hasSize(1);
    }
}
