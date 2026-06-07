package com.onesley.oneclick.modules.membercircle.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.LikeResultDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostCreateDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostFeedDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostsResultDto;
import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostsSummaryDto;
import static com.onesley.oneclick.shared.Temporals.toInstant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service « Circle » — modération des posts membres (C4.8c).
 *
 * <p>Lecture (liste enrichie auteur) en native SQL JOIN users (read-view, Modulith CLOSED, aucun
 * import cross-module). Écritures (approve/reject/delete) via repo JPA sur l'entité {@link MemberPost}.
 * Résumé = fonction pure {@link #summarize} (testée). SUPERADMIN-only (gardé au controller).
 */
@Service
public class MemberPostService {

    @PersistenceContext
    private EntityManager em;

    private final MemberPostRepository repo;
    private final MemberPostLikeRepository likeRepo;
    private final UserDirectoryApi userDirectory;

    public MemberPostService(MemberPostRepository repo, MemberPostLikeRepository likeRepo,
                             UserDirectoryApi userDirectory) {
        this.repo = repo;
        this.likeRepo = likeRepo;
        this.userDirectory = userDirectory;
    }

    // ─── A.1 — flux membre (création / feed / like) ──────────────────────────

    /** Création membre d'un post (status=pending). tenant résolu via l'auteur (read-view core-only). */
    @Transactional
    public MemberPostDto create(UUID authorId, MemberPostCreateDto dto) {
        UUID tenantId = userDirectory.tenantIdById(authorId)
            .orElseThrow(() -> new BadRequestException("Tenant introuvable pour l'auteur " + authorId));
        MemberPost p = repo.save(new MemberPost(UUID.randomUUID(), tenantId, authorId,
            dto.content(), dto.photoUrl(), dto.activityTag()));
        return new MemberPostDto(p.getId(), p.getAuthorId(), null, null, null,
            p.getContent(), p.getPhotoUrl(), p.getActivityTag(), p.getStatus(), null, p.getCreatedAt());
    }

    /**
     * Feed du mur communautaire : posts APPROUVÉS du tenant du membre, enrichis auteur + likes
     * (likedByMe pour le viewer), triés DESC, paginés. Native SQL read-view (aucun import cross-module).
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<MemberPostFeedDto> feed(UUID viewerId, int page, int size) {
        UUID tenantId = userDirectory.tenantIdById(viewerId).orElse(null);
        if (tenantId == null) {
            return List.of();
        }
        int safeSize = Math.max(1, Math.min(size, 100));
        var query = em.createNativeQuery("""
            SELECT p.id, p.author_id, u.first_name, u.last_name, u.avatar_url,
                   p.content, p.photo_url, p.activity_tag, p.created_at,
                   (SELECT COUNT(*) FROM member_post_likes l WHERE l.post_id = p.id) AS likes_count,
                   EXISTS(SELECT 1 FROM member_post_likes l2 WHERE l2.post_id = p.id AND l2.user_id = :viewer) AS liked_by_me
              FROM member_posts p
              LEFT JOIN users u ON u.id = p.author_id
             WHERE p.tenant_id = :t AND p.status = 'approved' AND p.deleted_at IS NULL
             ORDER BY p.created_at DESC
             LIMIT :lim OFFSET :off
            """)
            .setParameter("viewer", viewerId)
            .setParameter("t", tenantId)
            .setParameter("lim", safeSize)
            .setParameter("off", (long) Math.max(0, page) * safeSize);

        List<MemberPostFeedDto> rows = new ArrayList<>();
        for (Object[] p : (List<Object[]>) query.getResultList()) {
            rows.add(new MemberPostFeedDto(
                (UUID) p[0], (UUID) p[1], (String) p[2], (String) p[3], (String) p[4],
                (String) p[5], (String) p[6], (String) p[7],
                ((Number) p[9]).longValue(), (Boolean) p[10], toInstant(p[8])));
        }
        return rows;
    }

    /** Toggle like d'un post (idempotent) ; renvoie l'état + le compteur à jour. */
    @Transactional
    public LikeResultDto toggleLike(UUID postId, UUID userId) {
        MemberPost post = repo.findById(postId)
            .filter(p -> p.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("MemberPost", postId));
        Optional<MemberPostLike> existing = likeRepo.findByPostIdAndUserId(post.getId(), userId);
        boolean liked;
        if (existing.isPresent()) {
            likeRepo.delete(existing.get());
            liked = false;
        } else {
            likeRepo.save(new MemberPostLike(UUID.randomUUID(), post.getId(), userId));
            liked = true;
        }
        return new LikeResultDto(liked, likeRepo.countByPostId(post.getId()));
    }

    /** Liste des posts d'un tenant (filtre statut optionnel) + résumé. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public MemberPostsResultDto list(UUID tenantId, String status) {
        StringBuilder sql = new StringBuilder("""
            SELECT p.id, p.author_id, u.first_name, u.last_name, u.avatar_url,
                   p.content, p.photo_url, p.activity_tag, p.status, p.rejection_reason, p.created_at
              FROM member_posts p
              LEFT JOIN users u ON u.id = p.author_id
             WHERE p.tenant_id = :t AND p.deleted_at IS NULL
            """);
        if (status != null && !status.isBlank()) {
            sql.append(" AND p.status = :st");
        }
        sql.append(" ORDER BY p.created_at DESC");

        var query = em.createNativeQuery(sql.toString()).setParameter("t", tenantId);
        if (status != null && !status.isBlank()) {
            query.setParameter("st", status);
        }

        List<MemberPostDto> rows = new ArrayList<>();
        for (Object[] p : (List<Object[]>) query.getResultList()) {
            rows.add(new MemberPostDto(
                (UUID) p[0], (UUID) p[1], (String) p[2], (String) p[3], (String) p[4],
                (String) p[5], (String) p[6], (String) p[7], (String) p[8], (String) p[9],
                toInstant(p[10])));
        }
        // Le résumé est TOUJOURS calculé sur l'ensemble (tous statuts), pour des compteurs stables.
        MemberPostsSummaryDto summary = summarize(status == null || status.isBlank()
            ? rows
            : listAll(tenantId));
        return new MemberPostsResultDto(rows, summary);
    }

    @SuppressWarnings("unchecked")
    private List<MemberPostDto> listAll(UUID tenantId) {
        List<MemberPostDto> all = new ArrayList<>();
        for (Object[] p : (List<Object[]>) em.createNativeQuery("""
            SELECT p.id, p.status FROM member_posts p WHERE p.tenant_id = :t AND p.deleted_at IS NULL
            """).setParameter("t", tenantId).getResultList()) {
            all.add(new MemberPostDto((UUID) p[0], null, null, null, null, null, null, null,
                (String) p[1], null, null));
        }
        return all;
    }

    /** Approuve un post ; null si introuvable. */
    @Transactional
    public MemberPostDto approve(UUID id) {
        return transition(id, "approved", null);
    }

    /** Rejette un post (motif optionnel) ; null si introuvable. */
    @Transactional
    public MemberPostDto reject(UUID id, String reason) {
        return transition(id, "rejected", reason);
    }

    /** Soft-delete d'un post ; false si introuvable. */
    @Transactional
    public boolean delete(UUID id) {
        Optional<MemberPost> opt = repo.findById(id);
        if (opt.isEmpty() || opt.get().getDeletedAt() != null) {
            return false;
        }
        opt.get().setDeletedAt(Instant.now());
        return true;
    }

    private MemberPostDto transition(UUID id, String status, String reason) {
        Optional<MemberPost> opt = repo.findById(id);
        if (opt.isEmpty() || opt.get().getDeletedAt() != null) {
            return null;
        }
        MemberPost p = opt.get();
        p.setStatus(status);
        p.setRejectionReason("rejected".equals(status) ? reason : null);
        p.setReviewedAt(Instant.now());
        return new MemberPostDto(p.getId(), p.getAuthorId(), null, null, null,
            p.getContent(), p.getPhotoUrl(), p.getActivityTag(), p.getStatus(),
            p.getRejectionReason(), p.getCreatedAt());
    }

    /** Compteurs par statut — fonction pure, testable. */
    public static MemberPostsSummaryDto summarize(List<MemberPostDto> all) {
        long pending = 0, approved = 0, rejected = 0;
        for (MemberPostDto p : all) {
            switch (p.status() == null ? "" : p.status()) {
                case "pending" -> pending++;
                case "approved" -> approved++;
                case "rejected" -> rejected++;
                default -> { /* statut inconnu ignoré */ }
            }
        }
        return new MemberPostsSummaryDto(all.size(), pending, approved, rejected);
    }
}
