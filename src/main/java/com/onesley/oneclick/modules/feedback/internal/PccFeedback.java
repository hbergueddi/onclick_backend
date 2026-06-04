package com.onesley.oneclick.modules.feedback.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Avis d'un membre vers l'owner (PCC Lot 7) — thread {@code created → replied}.
 *
 * <p>Le membre ({@code memberId}, A) envoie un avis ({@code sentiment} happy/unhappy +
 * {@code category} libre + {@code comment} optionnel), éventuellement ciblé sur un resto du tenant
 * ({@code targetRestaurantId} ; NULL = avis général tenant-wide). L'owner (Adil) répond UNE fois
 * ({@code replyText}/{@code replyBy}/{@code replyAt}) ; le membre marque la réponse lue
 * ({@code replyReadByMember}).
 *
 * <p>Table {@code pcc_feedbacks} (V69). {@code tenantId} = tenant du membre, figé à la création
 * (scope des destinataires + isolation cross-tenant). Les noms/contact du membre et de l'owner,
 * et le nom du resto ciblé, sont résolus à la lecture (UserDirectoryApi / read-view), jamais
 * dénormalisés ici.
 */
@Entity
@Table(name = "pcc_feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PccFeedback extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** A — le membre auteur de l'avis (le caller qui envoie). */
    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    /** Tenant du membre — scope des destinataires + isolation cross-tenant. */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** {@code happy} (très content) / {@code unhappy} (mécontent) — CHECK en base. */
    @Column(name = "sentiment", nullable = false, length = 16, updatable = false)
    private String sentiment;

    /** Catégorie textuelle libre (ex: Propreté, Accueil, Personnel…), figée à l'envoi. */
    @Column(name = "category", nullable = false, length = 128, updatable = false)
    private String category;

    /** Commentaire libre optionnel (≤ 2000), figé à l'envoi. */
    @Column(name = "comment", length = 2000, updatable = false)
    private String comment;

    /** Resto PCC ciblé (owner notifié) ou {@code null} (avis général), figé à l'envoi. */
    @Column(name = "target_restaurant_id", updatable = false)
    private UUID targetRestaurantId;

    /** Réponse de l'owner — posée une seule fois (mutable jusqu'à la 1re réponse). */
    @Column(name = "reply_text", length = 2000)
    private String replyText;

    /** Owner/admin auteur de la réponse. */
    @Column(name = "reply_by")
    private UUID replyBy;

    /** Horodatage de la réponse. */
    @Column(name = "reply_at")
    private Instant replyAt;

    /** true quand le membre a lu la réponse (mutable côté membre). */
    @Column(name = "reply_read_by_member", nullable = false)
    private boolean replyReadByMember;

    /** Création d'un avis (workflow {@code created} : pas encore de réponse). */
    public PccFeedback(UUID id, UUID memberId, UUID tenantId, String sentiment, String category,
                       String comment, UUID targetRestaurantId) {
        this.id = id;
        this.memberId = memberId;
        this.tenantId = tenantId;
        this.sentiment = sentiment;
        this.category = category;
        this.comment = comment;
        this.targetRestaurantId = targetRestaurantId;
        this.replyReadByMember = false;
    }

    /** true si l'avis a déjà reçu une réponse (workflow {@code replied}). */
    public boolean isReplied() {
        return replyText != null;
    }

    /** Pose la réponse de l'owner (created → replied). Remet le flag de lecture à false. */
    public void applyReply(String replyText, UUID replyBy, Instant replyAt) {
        this.replyText = replyText;
        this.replyBy = replyBy;
        this.replyAt = replyAt;
        this.replyReadByMember = false;
    }

    /** Le membre marque la réponse comme lue. */
    public void markReplyRead() {
        this.replyReadByMember = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oe = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> te = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (te != oe) return false;
        return id != null && Objects.equals(id, ((PccFeedback) o).id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
