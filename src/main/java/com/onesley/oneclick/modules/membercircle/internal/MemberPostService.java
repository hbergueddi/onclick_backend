package com.onesley.oneclick.modules.membercircle.internal;

import com.onesley.oneclick.modules.membercircle.api.MemberPostDtos.MemberPostDto;
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

    public MemberPostService(MemberPostRepository repo) {
        this.repo = repo;
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
