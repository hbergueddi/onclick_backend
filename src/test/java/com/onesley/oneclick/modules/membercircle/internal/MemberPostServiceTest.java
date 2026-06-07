package com.onesley.oneclick.modules.membercircle.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.CommentCreateDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.LikeResultDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostCreateDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostsSummaryDto;
import com.onesley.oneclick.shared.events.MemberPostCommentedEvent;
import com.onesley.oneclick.shared.events.MemberPostLikedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires isolés (C4.8c summarize + A.1 create/toggleLike). Le flux SQL feed est
 * couvert par l'intégration.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class MemberPostServiceTest {

    @Mock MemberPostRepository repo;
    @Mock MemberPostLikeRepository likeRepo;
    @Mock MemberPostCommentRepository commentRepo;
    @Mock UserDirectoryApi userDirectory;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @InjectMocks MemberPostService service;

    private static MemberPostDto post(String status) {
        return new MemberPostDto(UUID.randomUUID(), UUID.randomUUID(), "A", "B", null,
            "contenu", null, null, status, null, null);
    }

    // ─── A.1 — create ────────────────────────────────────────────────────────

    @Test
    void create_resolvesTenant_andPersistsPending() {
        UUID author = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        when(userDirectory.tenantIdById(author)).thenReturn(Optional.of(tenant));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = service.create(author, new MemberPostCreateDto("Bonjour le club", null, "padel", null));
        assertThat(dto.status()).isEqualTo("pending");
        assertThat(dto.content()).isEqualTo("Bonjour le club");
        assertThat(dto.authorId()).isEqualTo(author);
        verify(repo).save(any());
    }

    @Test
    void create_noTenant_throwsBadRequest() {
        UUID author = UUID.randomUUID();
        when(userDirectory.tenantIdById(author)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(author, new MemberPostCreateDto("x", null, null, null)))
            .isInstanceOf(BadRequestException.class);
        verify(repo, never()).save(any());
    }

    // ─── A.1 — toggleLike ────────────────────────────────────────────────────

    private MemberPost approvedPost(UUID id) {
        return new MemberPost(id, UUID.randomUUID(), UUID.randomUUID(), "c", null, null);
    }

    @Test
    void toggleLike_firstTime_inserts_returnsLiked() {
        UUID postId = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        when(repo.findById(postId)).thenReturn(Optional.of(approvedPost(postId)));
        when(likeRepo.findByPostIdAndUserId(postId, user)).thenReturn(Optional.empty());
        when(likeRepo.countByPostId(postId)).thenReturn(1L);
        LikeResultDto r = service.toggleLike(postId, user);
        assertThat(r.liked()).isTrue();
        assertThat(r.likesCount()).isEqualTo(1);
        verify(likeRepo).save(any());
        // 1re pose du like sur le post d'autrui → notif publiée.
        verify(events).publishEvent(any(MemberPostLikedEvent.class));
    }

    @Test
    void toggleLike_selfLike_doesNotNotify() {
        UUID postId = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        MemberPost own = new MemberPost(postId, UUID.randomUUID(), author, "c", null, null);
        when(repo.findById(postId)).thenReturn(Optional.of(own));
        when(likeRepo.findByPostIdAndUserId(postId, author)).thenReturn(Optional.empty());
        when(likeRepo.countByPostId(postId)).thenReturn(1L);
        service.toggleLike(postId, author); // author aime SON post → pas de notif
        verify(events, never()).publishEvent(any(MemberPostLikedEvent.class));
    }

    @Test
    void toggleLike_existing_deletes_returnsUnliked() {
        UUID postId = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        when(repo.findById(postId)).thenReturn(Optional.of(approvedPost(postId)));
        when(likeRepo.findByPostIdAndUserId(postId, user))
            .thenReturn(Optional.of(new MemberPostLike(UUID.randomUUID(), postId, user)));
        when(likeRepo.countByPostId(postId)).thenReturn(0L);
        LikeResultDto r = service.toggleLike(postId, user);
        assertThat(r.liked()).isFalse();
        assertThat(r.likesCount()).isZero();
        verify(likeRepo).delete(any());
        // Un unlike ne notifie pas.
        verify(events, never()).publishEvent(any(MemberPostLikedEvent.class));
    }

    @Test
    void toggleLike_postNotFound_throws() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.toggleLike(UUID.randomUUID(), UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }

    // ─── A.2 — addComment ────────────────────────────────────────────────────

    @Test
    void addComment_onApprovedPost_persists() {
        UUID postId = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        MemberPost p = approvedPost(postId);
        p.setStatus("approved");
        when(repo.findById(postId)).thenReturn(Optional.of(p));
        when(commentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userDirectory.nameById(author)).thenReturn(Optional.empty());
        var dto = service.addComment(postId, author, new CommentCreateDto("Bien joué !", null));
        assertThat(dto.content()).isEqualTo("Bien joué !");
        assertThat(dto.authorId()).isEqualTo(author);
        verify(commentRepo).save(any());
        // Notif server-side émise (auteur du post ≠ commentateur).
        verify(events).publishEvent(any(MemberPostCommentedEvent.class));
    }

    @Test
    void addComment_carriesFilteredMentionRecipients() {
        UUID postId = UUID.randomUUID();
        UUID commenter = UUID.randomUUID();
        UUID postAuthor = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        MemberPost p = new MemberPost(postId, UUID.randomUUID(), postAuthor, "c", null, null);
        p.setStatus("approved");
        when(repo.findById(postId)).thenReturn(Optional.of(p));
        when(commentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userDirectory.nameById(commenter)).thenReturn(Optional.empty());
        // mentions = commentateur (exclu) + auteur du post (exclu) + un ami (gardé).
        service.addComment(postId, commenter, new CommentCreateDto("@x",
            List.of(commenter.toString(), postAuthor.toString(), friend.toString())));
        var captor = org.mockito.ArgumentCaptor.forClass(MemberPostCommentedEvent.class);
        verify(events).publishEvent(captor.capture());
        MemberPostCommentedEvent ev = captor.getValue();
        assertThat(ev.postAuthorRecipientId()).isEqualTo(postAuthor);
        assertThat(ev.mentionedRecipientIds()).containsExactly(friend); // self + post author exclus
        assertThat(ev.commenterName()).isEqualTo("Un membre"); // fallback (nameById vide)
    }

    @Test
    void addComment_postNotApproved_throwsBadRequest() {
        UUID postId = UUID.randomUUID();
        MemberPost pending = approvedPost(postId); // status = pending par défaut
        when(repo.findById(postId)).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service.addComment(postId, UUID.randomUUID(), new CommentCreateDto("x", null)))
            .isInstanceOf(BadRequestException.class);
        verify(commentRepo, never()).save(any());
    }

    @Test
    void addComment_postNotFound_throws() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addComment(UUID.randomUUID(), UUID.randomUUID(), new CommentCreateDto("x", null)))
            .isInstanceOf(NotFoundException.class);
    }

    // ─── A.2 — deleteComment (ABAC author-or-postauthor) ─────────────────────

    private MemberPostComment comment(UUID commentId, UUID postId, UUID authorId) {
        return new MemberPostComment(commentId, postId, authorId, "blabla", null);
    }

    @Test
    void deleteComment_byCommentAuthor_deletes() {
        UUID postId = UUID.randomUUID(), commentId = UUID.randomUUID(), author = UUID.randomUUID();
        when(commentRepo.findById(commentId)).thenReturn(Optional.of(comment(commentId, postId, author)));
        when(repo.findById(postId)).thenReturn(Optional.of(
            new MemberPost(postId, UUID.randomUUID(), UUID.randomUUID(), "c", null, null)));
        service.deleteComment(postId, commentId, author); // caller = auteur du commentaire
        verify(commentRepo).delete(any());
    }

    @Test
    void deleteComment_byPostAuthor_deletes() {
        UUID postId = UUID.randomUUID(), commentId = UUID.randomUUID();
        UUID commentAuthor = UUID.randomUUID(), postAuthor = UUID.randomUUID();
        when(commentRepo.findById(commentId)).thenReturn(Optional.of(comment(commentId, postId, commentAuthor)));
        when(repo.findById(postId)).thenReturn(Optional.of(
            new MemberPost(postId, UUID.randomUUID(), postAuthor, "c", null, null)));
        service.deleteComment(postId, commentId, postAuthor); // caller = auteur du post
        verify(commentRepo).delete(any());
    }

    @Test
    void deleteComment_byStranger_throwsForbidden() {
        UUID postId = UUID.randomUUID(), commentId = UUID.randomUUID();
        when(commentRepo.findById(commentId)).thenReturn(Optional.of(comment(commentId, postId, UUID.randomUUID())));
        when(repo.findById(postId)).thenReturn(Optional.of(
            new MemberPost(postId, UUID.randomUUID(), UUID.randomUUID(), "c", null, null)));
        assertThatThrownBy(() -> service.deleteComment(postId, commentId, UUID.randomUUID()))
            .isInstanceOf(ForbiddenException.class);
        verify(commentRepo, never()).delete(any());
    }

    @Test
    void deleteComment_notFound_throws() {
        when(commentRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteComment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteComment_wrongPost_throwsNotFound() {
        UUID commentId = UUID.randomUUID();
        // le commentaire appartient à un AUTRE post que celui de l'URL → 404 (garde-fou).
        when(commentRepo.findById(commentId))
            .thenReturn(Optional.of(comment(commentId, UUID.randomUUID(), UUID.randomUUID())));
        assertThatThrownBy(() -> service.deleteComment(UUID.randomUUID(), commentId, UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
        verify(commentRepo, never()).delete(any());
    }

    // ─── A.2 — mentionRecipients (fonction pure) ─────────────────────────────

    @Test
    void mentionRecipients_nullOrEmpty_isEmpty() {
        assertThat(MemberPostService.mentionRecipients(null, UUID.randomUUID(), UUID.randomUUID())).isEmpty();
        assertThat(MemberPostService.mentionRecipients(List.of(), UUID.randomUUID(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void mentionRecipients_excludesSelfAndPostAuthor_dedups_ignoresInvalid() {
        UUID commenter = UUID.randomUUID(), postAuthor = UUID.randomUUID();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        List<UUID> out = MemberPostService.mentionRecipients(
            List.of(commenter.toString(), postAuthor.toString(), a.toString(), a.toString(),
                "pas-un-uuid", "", b.toString()),
            commenter, postAuthor);
        assertThat(out).containsExactly(a, b); // self + postAuthor exclus, doublon a fusionné, invalides ignorés
    }

    @Test
    void summarize_empty_isAllZero() {
        MemberPostsSummaryDto s = MemberPostService.summarize(List.of());
        assertThat(s.total()).isZero();
        assertThat(s.pending()).isZero();
        assertThat(s.approved()).isZero();
        assertThat(s.rejected()).isZero();
    }

    @Test
    void summarize_countsByStatus() {
        MemberPostsSummaryDto s = MemberPostService.summarize(List.of(
            post("pending"), post("pending"), post("approved"), post("rejected"), post("weird")));
        assertThat(s.total()).isEqualTo(5);
        assertThat(s.pending()).isEqualTo(2);
        assertThat(s.approved()).isEqualTo(1);
        assertThat(s.rejected()).isEqualTo(1);  // statut inconnu non compté dans les buckets
    }
}
